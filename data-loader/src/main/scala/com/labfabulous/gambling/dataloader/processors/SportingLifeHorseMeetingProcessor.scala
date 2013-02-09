package com.labfabulous.gambling.dataloader.processors

import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.query.Imports._
import com.labfabulous.gambling.dataloader.html.{InvalidDetailPage, DetailsExtractor}
import com.labfabulous.gambling.dataloader.WebClient
import org.joda.time.DateTime

class SportingLifeHorseMeetingProcessor(htmlExtractor: DetailsExtractor) extends MeetingDetailsProcessor {
  RegisterJodaTimeConversionHelpers()
  private val dbCollection = MongoClient()("racing_data")("meetings")

  private def newLink (url: String): Boolean = {
    val q = MongoDBObject("url" -> url)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def process(url: String, date: DateTime, category: String) {
    try {
      if (newLink (url)) {
        val response = WebClient.get(url)
        if (WebClient.isError(response)) {
          success &= false
        } else {
          val dbObject: DBObject = MongoDBObject("url" -> url, "date" -> date, "category" -> category)
          if (response._1 == 200) {
            dbObject += "race" -> htmlExtractor.extract(response._2)
          } else if (response._1 == 404) {
            dbObject += "race" -> "Link appears to be broken"
          }
          dbCollection += dbObject
        }
      }
    } catch {
      case x: InvalidDetailPage => {
        println(s"ERROR (weird html): ${url}")
        success &= false
      }
      case _: Throwable => {
        println(s"ERROR (unexpected): ${url}")
        success &= false
      }
    }
  }
}
