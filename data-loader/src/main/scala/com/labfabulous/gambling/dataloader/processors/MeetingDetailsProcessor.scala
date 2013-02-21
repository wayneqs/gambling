package com.labfabulous.gambling.dataloader.processors

import org.joda.time.DateTime

abstract class MeetingDetailsProcessor {
  def process (url: String, date: DateTime, category: String): (Boolean, String)
}