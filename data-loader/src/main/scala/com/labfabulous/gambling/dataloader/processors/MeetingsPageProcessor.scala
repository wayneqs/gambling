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

class MeetingsPageProcessor(meetingsPageLinksExtractor: LinksExtractor, meetingDetailsProcessor: MeetingDetailsProcessor) extends Actor {
  RegisterJodaTimeConversionHelpers()

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  def processRaces(raceUrls: List[String], date: DateTime, category: String) {
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
    DateTime.now().withHour(0).withMinute(0).withMillisOfDay(0)
  }

  def doWork(work: WorkForDate) {
    val targetUrl = work.start.state + work.date.toString("dd-MM-yyyy")
    if (work.date <= (today + 1.day)) {
      WebClient.get(targetUrl) match {
        case (200, response) => processRaces(meetingsPageLinksExtractor.extract(response), work.date, work.start.category)
        case (404, response) => sender ! WorkPartiallyDone(work.start.category, work.date, "${targetUrl} => 404")
        case (code: Int, response) => sender ! WorkFailed(work.start.category, work.date, "${targetUrl} => ${code} => ${response}")
      }
    }
  }

  def receive = {
    case work: WorkForDate => doWork(work)
  }
}
