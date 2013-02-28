package com.labfabulous

import com.mongodb.casbah
import casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import org.scala_tools.time.Imports._
import akka.actor.{ActorRef, Props, OneForOneStrategy, Actor}
import com.labfabulous.DayWorker._
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import com.labfabulous.ProgressListener.Progress

object DayWorker {
  case class Start(epochDate: LocalDate)
  case class WorkForDate(start: Start, date: LocalDate)
  case class WorkDone(category: String, date: LocalDate)
  case class WorkPartiallyDone(category: String, date: LocalDate, message: String)
  case class WorkFailed(category: String, date: LocalDate, message: String)
}

class DayWorker(childWorker: Props)  extends Actor {
  RegisterJodaTimeConversionHelpers()
  val child = context.actorOf(childWorker)
  var progressListener: Option[ActorRef] = None

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  def doWorkFromDate(msg: Start, date: LocalDate) {
    if (date <= (LocalDate.now + 1.day)) {
      child.tell(WorkForDate(msg, date), self)
      doWorkFromDate(msg, (date + 1.day))
    }
  }

  def receive = {

    case msg: Start if (progressListener.isEmpty) =>
      progressListener = Some(sender)
      doWorkFromDate(msg, msg.epochDate)

    case p: Progress =>
      progressListener.get ! p
  }
}
