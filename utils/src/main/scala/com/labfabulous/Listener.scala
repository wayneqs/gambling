package com.labfabulous

import akka.actor.{ReceiveTimeout, Actor}
import scala.concurrent.duration._

class Listener extends Actor {
  context.setReceiveTimeout(30 minutes)

  def receive = {
    case ReceiveTimeout => {
      // No progress within 15 seconds, ServiceUnavailable
      println("Shutting down due to unavailable service")
      context.system.shutdown()
    }
    case _ =>
  }
}
