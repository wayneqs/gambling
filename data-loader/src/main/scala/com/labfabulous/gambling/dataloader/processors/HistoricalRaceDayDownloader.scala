package com.labfabulous.gambling.dataloader.processors

import akka.actor.{ActorRef, Actor}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.file.Paths
import com.mongodb.casbah.{MongoCollection, MongoDB}
import com.mongodb.casbah.commons.MongoDBObject
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import org.scala_tools.time.Imports._
import com.labfabulous.FileCreator
import com.labfabulous.http.HttpThrottler
import dispatch._
import com.labfabulous.DayWorker.WorkForDate
import com.labfabulous.ProgressListener.Progress
import scala.Some
import akka.actor.OneForOneStrategy

class HistoricalRaceDayDownloader(downloadIndex: MongoCollection,
                        thottler: HttpThrottler[String],
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

  private var progressListener: Option[ActorRef] = None

  def downloadAll(w: WorkForDate) {
    val raceDayUrl = s"""${baseUrl}/${w.date.toString("dd-MM-yyyy")}"""
    if (isNewUrl(raceDayUrl, x => x)) {
      println(s"Doing: ${raceDayUrl}")
      thottler.throttle(Http(url(raceDayUrl) OK as.String)) match {
        case (200, content) =>
          val allGood = gotRaceDay(raceDayUrl, content, w.date)
          if (allGood) raceDayComplete(raceDayUrl, w.date)
        case (code, content) =>
          println("mega fail on url ${raceDayUrl}, code ${code}")
      }
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
    progressListener.get ! Progress()
    downloadIndex += MongoDBObject("id" -> raceDayUrl)
  }

  private def gotRaceDay(raceDayUrl: String, content: String, date: LocalDate) = {
    val urls = extractor(content)
    urls forall (raceUrl => {
      if (isNewUrl(raceUrl)) {
        thottler.throttle(Http(url(raceUrl) OK as.String)) match {
          case (code, content) =>
            progressListener.get ! Progress()
            gotRace(code, raceUrl, content, date, raceDayUrl)
        }
      } else {
        true
      }
    })
  }

  private def gotRace(code: Int, raceUrl: String, content: String, date: LocalDate, raceDayUrl: String): Boolean = {
    code match {
      case 200 =>
        val doc: Document = Jsoup.parse(content)
        val contentElement = doc.getElementById("content")
        try {
          val savedLocation = fileCreator.create(Paths.get(downloadDir), contentElement.text().getBytes)
          downloadIndex += MongoDBObject("id" -> idFrom(raceUrl), "date" -> date.toDateTimeAtStartOfDay, "location" -> savedLocation.toString)
          true
        } catch {
          case e: Exception => println(s"Failed to write ${raceUrl} to file system: ${e.getMessage}")
          false
        }
      case 404 =>
        println(s"404 Not found: ${raceUrl}")
        true
      case value =>
        println(s"Got failed on ${raceUrl} with status code ${value}}")
        false
    }
  }

  def receive = {
    case w: WorkForDate =>
      if (progressListener.isEmpty) progressListener = Some(sender)
      if (w.date < LocalDate.today) downloadAll(w)
    case p: Progress => progressListener.get ! p
  }
}
