package com.labfabulous.gambling.dataloader.processors

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.query.Imports._
import com.labfabulous.gambling.dataloader.html.{InvalidDetailPage, DetailsExtractor}
import com.labfabulous.gambling.dataloader.WebClient
import org.joda.time.LocalDate

class SportingLifeHorseMeetingProcessor(mongo: MongoClient, htmlExtractor: DetailsExtractor) extends MeetingDetailsProcessor {
  RegisterJodaTimeConversionHelpers()
  private val dbCollection = mongo("racing_data")("meetings")

  private def newLink (url: String): Boolean = {
    val q = MongoDBObject("url" -> url)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def process(url: String, date: LocalDate, category: String): (Boolean, String) = {
    try {
      if (newLink (url)) {
        val dbObject: DBObject = MongoDBObject("url" -> url, "date" -> date.toDateTimeAtStartOfDay, "category" -> category)
        WebClient.get(url) match {
          case (200, response) =>
            dbObject += "race" -> htmlExtractor.extract(response)
            dbCollection += dbObject
            (true, "OK")
          case (404, response) =>
            val dbObject: DBObject = MongoDBObject("url" -> url, "date" -> date.toDateTimeAtStartOfDay, "category" -> category)
            dbObject += "race" -> "Link appears to be broken"
            dbCollection += dbObject
            (true, s"Link broken: ${url}")
          case (code: Int, response) =>
            (false, "${targetUrl} => ${code} => ${response}")
        }
      } else {
        (true, s"OK")
      }

    } catch {
      case x: InvalidDetailPage => {
        println()
        (false, s"ERROR (weird html): ${url}")
      }
      case msg: Throwable => {
        (false, s"ERROR (unexpected): ${url}: ${msg}")
      }
    }
  }
}
