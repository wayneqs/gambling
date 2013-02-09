package com.labfabulous.gambling.dataloader

import org.joda.time.DateTime
import akka.actor.Props

object RaceDataProtocol {
  case class ProcessMeetings(baseUrl: String, processDate: DateTime, url: String)
  case class DayProcessed(baseUrl: String, processDate: DateTime)
  case class Race(url: String, processDate: DateTime)
  case class Get(url: String)
  case class OK()
}
