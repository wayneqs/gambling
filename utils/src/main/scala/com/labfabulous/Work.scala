package com.labfabulous

import org.scala_tools.time.Imports._
import com.labfabulous.DayWorker.{Result, Start}

abstract class Work {
  def doWork(msg: Start, date: DateTime): Result
}
