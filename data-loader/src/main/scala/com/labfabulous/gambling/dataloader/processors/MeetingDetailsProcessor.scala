package com.labfabulous.gambling.dataloader.processors

import org.joda.time.LocalDate

abstract class MeetingDetailsProcessor {
  def process (url: String, date: LocalDate, category: String): (Boolean, String)
}