package com.labfabulous

import com.mongodb.casbah
import casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import casbah.commons.MongoDBObject
import com.mongodb.casbah.query.Imports._
import casbah.MongoClient
import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import akka.actor.{OneForOneStrategy, Actor}
import com.labfabulous.DayWorker.ErrorDie
import com.labfabulous.DayWorker.ErrorOK
import com.labfabulous.DayWorker.Start
import com.labfabulous.DayWorker.OK
import scala.Some
import com.labfabulous.DayWorker.DayProcessed
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}

object DayWorker {
  case class Start(baseUrl: String, category: String, epochDate: DateTime)
  case class WorkForDate(start: Start, processDate: DateTime)
  case class DayProcessed(baseUrl: String, category: String, processDate: DateTime)
  abstract class Result
  case class OK(start:Start, processDate:DateTime) extends Result
  case class ErrorOK(start:Start, processDate:DateTime, error:String) extends Result
  case class ErrorDie(start:Start, processDate:DateTime, error:String) extends Result
}

class DayWorker(work: Work)  extends Actor {
  RegisterJodaTimeConversionHelpers()
  private val mongoClient = MongoClient()("racing_data")

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  private def today = {
    DateTime.now().withHour(0).withMinute(0).withMillisOfDay(0)
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

  private def updateCurrentPointer(event: DayProcessed) {
    // don't update the current pointer beyond yesterday's date
    // that way the system should always work around the yesterday today boundary
    if (event.processDate < today) {
      val pointersCollection = mongoClient("pointers")
      val q = ("current" $exists true) ++ ("category" -> event.category)
      val p = $set(Seq("current" -> event.processDate))
      pointersCollection.update(q, p)
    }
  }

  def handleOK(msg: Start, date:DateTime) {
    updateCurrentPointer(DayProcessed(msg.baseUrl, msg.category, date))
    doWorkFromDate(msg, date + 1.day)
  }

  def handleErrorOK(msg: Start, date:DateTime, error:String) {
    println(error)
    doWorkFromDate(msg, date + 1.day)
  }

  def doWorkFromDate(msg: Start, date: DateTime) {
    if (date <= (today + 1.day)) {
      work.doWork(msg, date) match {
        case ok: OK => handleOK(ok.start, ok.processDate)
        case err: ErrorOK => handleErrorOK(err.start, err.processDate, err.error)
        case die: ErrorDie => println(die)
      }
    }
  }

  private def getCurrentProcessingDate(msg: Start) = {
    val pointersCollection = mongoClient("pointers")
    val pointerName: String = "current"
    val dbObjectForUrl = MongoDBObject("category" -> msg.category)
    val q = (pointerName $exists true) ++ dbObjectForUrl
    val defaultValue = dbObjectForUrl ++ (pointerName -> msg.epochDate)
    getPointer(pointerName, q, pointersCollection, defaultValue)
  }

  private def doWork(msg: Start) {
    val currentProcessingDate = getCurrentProcessingDate(msg)
    if (currentProcessingDate <= (today + 1.day)) {
      work.doWork(msg, currentProcessingDate) match {
        case ok: OK => handleOK(ok.start, ok.processDate)
        case err: ErrorOK => handleErrorOK(err.start, err.processDate, err.error)
        case die: ErrorDie => println(die)
      }
    }
  }

  def receive = {
    case msg: Start => {
      doWork(msg)
    }
  }
}
