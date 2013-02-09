package com.labfabulous.gambling.dataloader

import dispatch._
import retry.{Success, Backoff}
import java.util.concurrent.TimeUnit

object WebClient {

  val defaultSuccess = new Success[Either[Throwable, String]](either => {
    either match {
      case Right(html: String) => true
      case Left(StatusCode(404)) => true
      case _ => false
    }
  })

  def get (target:String): (Int, String) = {
    get (target, defaultSuccess)
  }

  def get (target:String, success: Success[Either[Throwable, String]]): (Int, String) = {
    val svc = url(target)
    val resultPromise = Http(svc OK as.String).either

    val result = Backoff(max = 7, delay = new Duration(750, TimeUnit.MILLISECONDS))(resultPromise)(success)
    result() match {
      case Right(html: String) => (200, html)
      case Left(StatusCode(404)) => (404, "")
      case Left(StatusCode(value: Int)) => (value, "ERROR")
      case _ => (000, "ERROR")
    }
  }

  def isError (value: (Int, String)) = {
    value._2 == "ERROR"
  }
}
