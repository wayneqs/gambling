package com.labfabulous.gambling.dataloader.processors

import org.joda.time.DateTime

abstract class MeetingDetailsProcessor {
  var success = true
  def process (url: String, date: DateTime, category: String)
}