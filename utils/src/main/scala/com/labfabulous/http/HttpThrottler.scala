package com.labfabulous.http

import dispatch._
import java.util.concurrent.TimeUnit
import dispatch.retry.{Backoff, Success}

class HttpThrottler[T](empty: T) {

  val defaultSuccess = new Success[Either[Throwable, T]](either => {
    either match {
      case Right(result: T) => true
      case Left(StatusCode(404)) => true
      case _ => false
    }
  })

  def throttle (promise: Promise[T]): (Int, T) = {
    throttle (promise, defaultSuccess)
  }

  def throttle (promise: Promise[T], success: Success[Either[Throwable, T]]): (Int, T) = {
    val result = Backoff(max = 7, delay = new Duration(750, TimeUnit.MILLISECONDS))(promise.either)(success)
    result() match {
      case Right(bytes: T) => (200, bytes)
      case Left(StatusCode(404)) => (404, empty)
      case Left(StatusCode(value: Int)) => (value, empty)
      case _ => (000, empty)
    }
  }

  def isError (value: (Int, T)) = {
    value._2 == empty
  }
}
