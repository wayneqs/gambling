package com.labfabulous.gambling.dataloader.processors

import akka.actor.{ActorRef, Props, Actor}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Paths
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
import com.labfabulous.FileCreator

class RaceDayDownloader(db: MongoDB,
                        downloaderProps: Props,
                        baseUrl: String,
                        downloadDir: String,
                        fileCreator: FileCreator,
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
      println(s"Doing: ${raceDayUrl}")
      downloader ! Get(raceDayUrl, w.date)
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

  private def raceDayComplete(raceDayUrl: String, date: LocalDate) {
    println(s"Done: ${raceDayUrl}")
    atomic {
      implicit txn =>
        urlState.remove(raceDayUrl)
    }
    if (date < LocalDate.now) downloadIndex += MongoDBObject("id" -> raceDayUrl)
  }

  private def checkForRaceDayCompletion(url: String, date: LocalDate) {
    atomic {
      implicit txn =>
        val tuple = urlState.get(url).get
        if (tuple._1 == tuple._2()) raceDayComplete(url, date)
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
    urls.size match {
      case 0 => raceDayComplete(g.url, g.date)
      case _ => processUrls(g.url, g.date, urls)
    }
  }

  private def processUrls(raceDayUrl: String, date: LocalDate, raceUrls: List[String]) {
    raceUrls foreach (url => {
      if (isNewUrl(url)) {
        downloader ! Get(url, date, Some(url))
      } else {
        increment(raceDayUrl)
        checkForRaceDayCompletion(raceDayUrl, date)
      }})
  }
  private def gotRace(g: Got) {
    g.status match {
      case 200 =>
        val doc: Document = Jsoup.parse(g.content)
        val content = doc.getElementById("content")
        try {
          val savedLocation = fileCreator.create(Paths.get(downloadDir), content.text().getBytes)
          downloadIndex += MongoDBObject("id" -> idFrom(g.url), "date" -> g.date.toDateTimeAtStartOfDay, "location" -> savedLocation.toString)
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
