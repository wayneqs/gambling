package com.labfabulous

import akka.actor.{ReceiveTimeout, Actor}
import scala.concurrent.duration._
import com.labfabulous.DayWorker.Progress

class TimeOutListener(timeOut: FiniteDuration = 15 minutes) extends Actor {
  context.setReceiveTimeout(timeOut)
  var count = 0

  def receive = {
    case ReceiveTimeout => {
      println("Shutting down due to time out")
      context.system.shutdown()
    }
    case _: Progress =>  {
      count += 1
      if (count % 5000 == 0) print(".")
    }
  }
}
