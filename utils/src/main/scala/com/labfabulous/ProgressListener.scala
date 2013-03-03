package com.labfabulous

import akka.actor.{ReceiveTimeout, Actor}
import scala.concurrent.duration._
import com.labfabulous.ProgressListener._
import org.joda.time.DateTime

object ProgressListener {
  case class Progress()
}
class ProgressListener(timeOut: FiniteDuration = 15 minutes) extends Actor {
  context.setReceiveTimeout(timeOut)

  def receive = {
    case ReceiveTimeout =>
      println(s"Shutting down due to time out ${DateTime.now()}")
      context.system.shutdown()
    case _: Progress =>
  }
}
