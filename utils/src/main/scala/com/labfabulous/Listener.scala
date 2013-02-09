package com.labfabulous

import akka.actor.Actor
import com.labfabulous.Listener.OK

object Listener {
  case class OK()
}
class Listener extends Actor {
  def receive = {
    case OK =>
  }
}
