package com.labfabulous.http

import akka.actor.Actor
import com.labfabulous.http.Downloader._
import dispatch._
import org.joda.time.LocalDate
import com.labfabulous.ProgressListener.Progress

object Downloader {
  case class Get(url: String, date: LocalDate, tag: Option[String] = None)
  case class Got(url: String, date: LocalDate, status: Int, content: String, tag: Option[String])
}
class Downloader(thottler: HttpThrottler[String]) extends Actor {

  def get(get: Get) {
    thottler.throttle(Http(url(get.url) OK as.String)) match {
      case (code, content) =>
        sender ! Progress()
        sender ! Got(get.url, get.date, code, content, get.tag)
      case _ => println(s"failed to download ${get.url}: total failure.")
    }
  }

  def receive = {
    case g: Get => get(g)
  }
}
