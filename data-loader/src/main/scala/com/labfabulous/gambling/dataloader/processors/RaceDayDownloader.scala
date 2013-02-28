package com.labfabulous.gambling.dataloader.processors

import akka.actor.{ActorRef, Props, Actor}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.{StandardOpenOption, Paths, Files}
import org.bson.types.ObjectId
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.MongoDBObject
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import concurrent.stm._
import org.scala_tools.time.Imports._
import com.labfabulous.DayWorker.WorkForDate
import com.labfabulous.http.Downloader.Got
import com.labfabulous.ProgressListener.Progress
import com.labfabulous.http.Downloader.Get
import scala.Some
import akka.actor.OneForOneStrategy

class RaceDayDownloader(db: MongoDB,
                        downloaderProps: Props,
                        baseUrl: String,
                        downloadDir: String,
                        extractor: (String => List[String])) extends Actor {

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  private val downloadIndex = db("downloadIndex")
  private val downloader = context.actorOf(downloaderProps)
  private var progressListener: Option[ActorRef] = None
  private val urlState = TMap[String, (Int, Ref[Int])]()

  def downloadAll(w: WorkForDate) {
    val raceDayUrl = s"""${baseUrl}/${w.date.toString("dd-MM-yyyy")}"""
    if (isNewUrl(raceDayUrl, x => x)) {
      println(s"Doing: ${w.date.toString("dd-mm-yyyy")} ${raceDayUrl}")
      downloader ! Get(raceDayUrl, w.date)
    } else {
      println(s"Skipping: ${w.date.toString("dd-mm-yyyy")} ${raceDayUrl}")
    }
  }

  def got(g: Got) {
    if (g.url.matches( """.+/(results|racecards)/\d{2}-\d{2}-\d{4}/?.+""")) {
      gotRace(g)
    } else {
      gotRaceDay(g)
    }
  }

  private def idFrom(url: String) = {
    val reversedUrl = url.reverse
    reversedUrl.substring(reversedUrl.indexOf("/") + 1).reverse
  }

  private def isNewUrl(url: String, idBuilder: (String => String) = idFrom) = {
    val q = MongoDBObject("id" -> idBuilder(url))
    downloadIndex.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  private def checkForRaceDayCompletion(url: String, date: LocalDate) {
    def raceDayProcessingComplete {
      println(s"Done: ${date.toString("dd-mm-yyyy")} ${url}")
      if (date < LocalDate.now) downloadIndex += MongoDBObject("id" -> url)
    }
    atomic {
      implicit txn =>
        val tuple = urlState.get(url).get
        if (tuple._1 == tuple._2()) raceDayProcessingComplete
    }
  }

  private def increment(url: String) {
    atomic {
      implicit txn =>
        urlState.get(url).get._2 transform (_ + 1)
    }
    progressListener.get ! Progress()
  }
  private def gotRaceDay(g: Got) {
    val urls = extractor(g.content)
    atomic {
      implicit txn =>
        urlState += (g.url ->(urls.size, Ref(0)))
    }
    urls foreach (url =>
      if (isNewUrl(url)) {
        downloader ! Get(url, g.date, Some(g.url))
      } else {
        increment(g.url)
        checkForRaceDayCompletion(g.url, g.date)
      })
  }

  private def gotRace(g: Got) {
    g.status match {
      case 200 =>
        val doc: Document = Jsoup.parse(g.content)
        val content = doc.getElementById("content")
        val id = new ObjectId
        val location = Paths.get(downloadDir, id.toString)
        try {
          Files.write(location, content.text().getBytes, StandardOpenOption.CREATE)
          downloadIndex += MongoDBObject("id" -> idFrom(g.url), "date" -> g.date.toDateTimeAtStartOfDay, "location" -> location.toString)
          if (g.tag.isDefined) increment(g.tag.get)
        } catch {
          case e: Exception => println(s"Failed to write ${g.url} to file system: ${e.getMessage}")
        }
      case 404 =>
        println(s"404 Not found: ${g.url}")
        if (g.tag.isDefined) increment(g.tag.get)
      case value =>
        println(s"Got failed on ${g.url} with status code ${value}}")
    }
    if (g.tag.isDefined) checkForRaceDayCompletion(g.tag.get, g.date)
  }

  def receive = {
    case w: WorkForDate =>
      if (progressListener.isEmpty) progressListener = Some(sender)
      downloadAll(w)
    case g: Got => got(g)
    case p: Progress => progressListener.get ! p
  }
}
