package com.labfabulous.gambling.dataloader.processors

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.query.Imports._
import com.mongodb.casbah
import casbah.MongoClient
import org.scala_tools.time.Imports._
import scala.Some
import com.labfabulous.gambling.dataloader.RaceDataProtocol.DayProcessed
import org.joda.time.DateTime
import com.labfabulous.gambling.dataloader.html.LinksExtractor
import com.labfabulous.gambling.dataloader.WebClient
import akka.actor.{OneForOneStrategy, Actor}
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.labfabulous.DayWorker.Start

class MeetingsPageProcessor(meetingsPageLinksExtractor: LinksExtractor, meetingDetailsProcessor: MeetingDetailsProcessor) extends Actor {
  RegisterJodaTimeConversionHelpers()
  private val mongoClient = MongoClient()("racing_data")


  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  private def getPointer(field: String, q: MongoDBObject, collection: casbah.MongoCollection, defaultValue: MongoDBObject): DateTime = {
    collection.findOne(q) match {
      case Some(pointer: DBObject) => pointer.get(field).asInstanceOf[DateTime]
      case _ => {
        collection += defaultValue
        getPointer(field, q, collection, defaultValue)
      }
    }
  }

  def processRaces(raceUrls: List[String], date: DateTime, category: String) {
    raceUrls.foreach(url => meetingDetailsProcessor.process(url, date, category))
  }

  def today = {
    DateTime.now().withHour(0).withMinute(0).withMillisOfDay(0)
  }

  def fillForDate(msg: Start, date: DateTime) {
    val targetUrl = msg.baseUrl + date.toString("dd-MM-yyyy")
    if (date <= (today + 1.day)) {
      val response = WebClient.get(targetUrl)
      if (!WebClient.isError(response)) {
        if (response._1 != 404) { // 404 means the link is broken and we can just move on to the next link
          processRaces(meetingsPageLinksExtractor.extract(response._2), date, msg.baseUrl)
        }
        handle(DayProcessed(msg.baseUrl, date))
        fillForDate(msg, date + 1.day)
      }
    }
  }

  private def fillRecentData(msg: Start) {
    val pointersCollection = mongoClient("pointers")
    val pointerName: String = "current"
    val dbObjectForUrl = MongoDBObject("url" -> msg.baseUrl)
    val q = (pointerName $exists true) ++ dbObjectForUrl
    val defaultValue = dbObjectForUrl ++ (pointerName -> msg.epochDate)
    val current = getPointer(pointerName, q, pointersCollection, defaultValue)
    fillForDate(msg, current)
  }


  private def handle(event: DayProcessed) {
    // don't update the current pointer beyond yesterday's date
    // that way the system should always work around the yesterday today boundary
    if (meetingDetailsProcessor.success && event.processDate < today) {
      val pointersCollection = mongoClient("pointers")
      val q = ("current" $exists true) ++ ("url" -> event.baseUrl)
      val p = $set(Seq("current" -> event.processDate))
      pointersCollection.update(q, p)
    }
  }

  def process(start: Start) {
    fillRecentData(start)
  }

  def receive = {
    case start: Start => fillRecentData(start)
  }
}
