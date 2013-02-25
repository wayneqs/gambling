package com.labfabulous

import com.mongodb.casbah
import casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import casbah.commons.MongoDBObject
import casbah.MongoClient
import org.scala_tools.time.Imports._
import akka.actor.{ActorRef, Props, OneForOneStrategy, Actor}
import com.labfabulous.DayWorker._
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}

object DayWorker {
  case class Start(category: String, epochDate: LocalDate, state: String = "")
  case class WorkForDate(start: Start, date: LocalDate)
  case class WorkDone(category: String, date: LocalDate)
  case class WorkPartiallyDone(category: String, date: LocalDate, message: String)
  case class WorkFailed(category: String, date: LocalDate, message: String)
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
    LocalDate.now
  }

  def handleOK(category: String, date: LocalDate) {
    println(s"${date} for ${category} is complete")
    saveDateForCategory(category, date)
    progressListener.get ! Progress()
  }

  def handleErrorOK(category: String, date: LocalDate, error: String) {
    println(s"PARTIAL: ${date} for ${category} is complete: ${error}")
    saveDateForCategory(category, date)
    progressListener.get ! Progress()
  }

  def handleError(msg: WorkFailed) {
    println(s"FAILURE: ${msg.date} for ${msg.category} is complete: ${msg.message}")
    progressListener.get ! Progress()
  }

  def saveDateForCategory(category: String, date: LocalDate) {
    val dbObject = MongoDBObject("date" -> date.toDateTimeAtStartOfDay, "category" -> category)
    dbCollection += dbObject
  }

  def isNewDateForCategory(category: String, date: LocalDate): Boolean = {
    val q = MongoDBObject("date" -> date.toDateTimeAtStartOfDay, "category" -> category)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def doWorkFromDate(msg: Start, date: LocalDate) {
    if (date >= msg.epochDate) {
//    if (date <= (today + 1.day)) {
      if (isNewDateForCategory(msg.category, date)) {
        println(s"${date} is new for ${msg.category}")
        child.tell(WorkForDate(msg, date), self)
      }
//      doWorkFromDate(msg, (date + 1.day))
      doWorkFromDate(msg, (date - 1.day))
    }
  }

  def receive = {

    case msg: Start if (progressListener.isEmpty) =>
      progressListener = Some(sender)
//      doWorkFromDate(msg, msg.epochDate)
      doWorkFromDate(msg, today - 1.day)

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
