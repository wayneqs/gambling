package com.labfabulous.gambling.dataloader.processors

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import org.scala_tools.time.Imports._
import org.joda.time.DateTime
import com.labfabulous.gambling.dataloader.html.LinksExtractor
import com.labfabulous.gambling.dataloader.WebClient
import akka.actor.Actor
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.labfabulous.DayWorker._
import com.labfabulous.DayWorker.WorkForDate
import akka.actor.OneForOneStrategy
import com.labfabulous.DayWorker.WorkFailed
import dispatch.{as, url, Promise}
import com.labfabulous.ProgressListener.Progress

class MeetingsPageProcessor(meetingsPageLinksExtractor: LinksExtractor, meetingDetailsProcessor: MeetingDetailsProcessor) extends Actor {
  RegisterJodaTimeConversionHelpers()

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  def processRaces(raceUrls: List[String], date: LocalDate, category: String) {
    raceUrls.forall(url => {
      meetingDetailsProcessor.process(url, date, category) match {
        case (true, message) =>
          if (message != "OK") println(message)
          sender ! Progress()
          true
        case (false, message) =>
          println(message)
          sender ! Progress()
          false
      }
    }) match {
      case true => sender ! WorkDone(category, date)
      case _ => sender ! WorkFailed(category, date, "not all urls were processed for date ${date}")
      }
  }

  def today = {
    LocalDate.now
  }

  def doWork(work: WorkForDate) {
//    val promise: Promise[String] = Http(url(get.url) OK as.String)
//    throttler.throttle(promise) match {
//      case (200, response) => save(response, get)
//      case (404, response) => println(s"ERROR: ${404} => ${get.url}")
//      case (code: Int, response) => println(s"ERROR: code ${code} => ${get.url}")
//    }
    val targetUrl = work.start.toString + work.date.toString("dd-MM-yyyy")
    WebClient.get(targetUrl) match {
      case (200, response) => processRaces(meetingsPageLinksExtractor.extract(response), work.date, work.start.toString)
      case (404, response) => sender ! WorkPartiallyDone(work.start.toString, work.date, "${targetUrl} => 404")
      case (code: Int, response) => sender ! WorkFailed(work.start.toString, work.date, "${targetUrl} => ${code} => ${response}")
    }
  }

  def receive = {
    case work: WorkForDate => doWork(work)
  }
}
