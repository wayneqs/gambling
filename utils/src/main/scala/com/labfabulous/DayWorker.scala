package com.labfabulous

import com.mongodb.casbah
import casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import casbah.commons.MongoDBObject
import casbah.MongoClient
import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import akka.actor.{Props, OneForOneStrategy, Actor}
import com.labfabulous.DayWorker._
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import com.labfabulous.DayWorker.Start

object DayWorker {
  case class Start(baseUrl: String, category: String, epochDate: DateTime)
  case class WorkForDate(start: Start, date: DateTime)
  case class WorkDone(category: String, date: DateTime)
  case class WorkPartiallyDone(category: String, date: DateTime, message: String)
  case class WorkFailed(category: String, date: DateTime, message: String)
  case class Ping()
}

class DayWorker(mongo: MongoClient, childWorker: Props)  extends Actor {
  RegisterJodaTimeConversionHelpers()
  private val dbCollection = mongo("racing_data")("pointers")
  val child = context.actorOf(childWorker)

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  private def today = {
    DateTime.now().withHour(0).withMinute(0).withMillisOfDay(0)
  }

  def handleOK(category: String, date: DateTime) {
    saveDateForCategory(category, date)
    sender ! Ping()
  }

  def handleErrorOK(category: String, date: DateTime, error: String) {
    println(s"processing of date ${date} failed with error: ${error}")
    saveDateForCategory(category, date)
    sender ! Ping()
  }

  def handleError(msg: WorkFailed) {
    println(s"processing of date ${msg.date} failed with error: ${msg.message}")
    sender ! Ping()
  }

  def saveDateForCategory(category: String, date: DateTime) {
    val dbObject = MongoDBObject("date" -> date, "category" -> category)
    dbCollection += dbObject
  }

  def isNewDateForCategory(category: String, date: DateTime): Boolean = {
    val q = MongoDBObject("date" -> date, "category" -> category)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def doWorkFromDate(msg: Start, date: DateTime) {
    if (date <= (today + 1.day) && isNewDateForCategory(msg.category, date)) {
      child.tell(WorkForDate(msg, date), self)
      doWorkFromDate(msg, date + 1.day)
    }
  }

  def receive = {
    case msg: Start => doWorkFromDate(msg, msg.epochDate)
    case msg: WorkDone => handleOK(msg.category, msg.date)
    case msg: WorkPartiallyDone => handleErrorOK(msg.category, msg.date, msg.message)
    case msg: WorkFailed => handleError(msg)
  }
}
