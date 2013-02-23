package com.labfabulous

import com.mongodb.casbah
import casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import casbah.commons.MongoDBObject
import casbah.MongoClient
import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import akka.actor.{ActorRef, Props, OneForOneStrategy, Actor}
import com.labfabulous.DayWorker._
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import com.labfabulous.DayWorker.Start

object DayWorker {
  case class Start(category: String, epochDate: DateTime, state: String = "")
  case class WorkForDate(start: Start, date: DateTime)
  case class WorkDone(category: String, date: DateTime)
  case class WorkPartiallyDone(category: String, date: DateTime, message: String)
  case class WorkFailed(category: String, date: DateTime, message: String)
  case class Progress()
}

class DayWorker(mongo: MongoClient, childWorker: Props)  extends Actor {
  RegisterJodaTimeConversionHelpers()
  private val dbCollection = mongo("racing_data")("pointers")
  val child = context.actorOf(childWorker)
  var progressListener: Option[ActorRef] = None

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
    progressListener.get ! Progress()
  }

  def handleErrorOK(category: String, date: DateTime, error: String) {
    println(s"processing of date ${date} failed with error: ${error}")
    saveDateForCategory(category, date)
    progressListener.get ! Progress()
  }

  def handleError(msg: WorkFailed) {
    println(s"processing of date ${msg.date} failed with error: ${msg.message}")
    progressListener.get ! Progress()
  }

  def saveDateForCategory(category: String, dateTime: DateTime) {
    val dbObject = MongoDBObject("date" -> dateTime.date, "category" -> category)
    dbCollection += dbObject
  }

  def isNewDateForCategory(category: String, dateTime: DateTime): Boolean = {
    val q = MongoDBObject("date" -> dateTime.date, "category" -> category)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def doWorkFromDate(msg: Start, date: DateTime) {
    if (date <= (today + 1.day)) {
      if (isNewDateForCategory(msg.category, date)) {
        println(s"${date.toString("dd-MM-YYYY")} is new for ${msg.category}")
        child.tell(WorkForDate(msg, date), self)
      }
      doWorkFromDate(msg, (date + 1.day))
    }
  }

  def receive = {

    case msg: Start if (progressListener.isEmpty) =>
      progressListener = Some(sender)
      doWorkFromDate(msg, msg.epochDate)

    case msg: WorkDone =>
      handleOK(msg.category, msg.date)

    case msg: WorkPartiallyDone =>
      handleErrorOK(msg.category, msg.date, msg.message)

    case msg: WorkFailed =>
      handleError(msg)

    case p: Progress =>
      progressListener.get ! p
  }
}
