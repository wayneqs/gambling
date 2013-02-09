package com.labfabulous

object Epocher {
  def get() = {
    import org.scala_tools.time.Imports._
    new DateTime(2008, 01, 28, 0, 0)
  }
}
