package com.labfabulous.gambling.dataloader.html

import org.jsoup.Jsoup
import org.jsoup.select.Elements
import com.mongodb.casbah.commons.{MongoDBListBuilder, MongoDBList, MongoDBObject}
import collection.JavaConversions._
import com.mongodb.casbah.commons.Imports.DBObject
import com.labfabulous.gambling.dataloader.models.{Player, Weight, Odds, Distance}

class SportingLifeRacePageCardExtractor extends DetailsExtractor {

  def extractAndChange(original: Elements, index: Int): DBObject = {
    val runner = original.get(index)
    val fields = runner.select("td")
    val cd = runner.select(".sui-cd").size() == 1
    val c = runner.select(".sui-c").size() == 1
    val d = runner.select(".sui-d").size() == 1
    val bf = runner.select(".sui-bf").size() == 1
    val DaysSinceLastRun = """(\s*(\(.*\))*\s*(\d+))|((\d+))""".r
    val daysSinceLastRun = fields.get(2).select("em.note").text() match {
      case DaysSinceLastRun(_:String,_,days,_,_) => days.toInt
      case DaysSinceLastRun(_,_,_,_:String,days) => days.toInt
      case _ => -1 // error
    }
    val ageText = fields.get(3).text().trim
    val ratingText = fields.get(7).text().trim
    val builder = MongoDBObject.newBuilder
    builder += "horse" -> Player.getPlayer(fields.get(2)).dbObject
    builder += "bf" -> bf
    builder += "cw" -> (c || cd)
    builder += "dw" -> (d || cd)
    builder += "daysSinceLastRun" -> daysSinceLastRun
    builder += "form" -> fields.get(1).text().trim
    builder += "age" -> (if (ageText == "") 0 else ageText.toInt)
    builder += "odds" -> new Odds(fields.get(9).text().trim).frac
    builder += "rating" -> (if (ratingText == "") 0 else ratingText.toInt)
    builder += "weight" -> new Weight(fields.get(4).text().trim).lbs
    builder += "jockey" -> Player.getPlayer(fields.get(6)).dbObject
    builder += "trainer" -> Player.getPlayer(fields.get(5)).dbObject
    builder.result()
  }

  def build(original: Elements, builder: MongoDBListBuilder, index: Int): MongoDBList = {
    if (index == original.length) {
      builder.result()
    } else {
      builder += extractAndChange(original, index)
      build(original, builder, index+1)
    }
  }

  def extract(raceHtml: String): DBObject = {
    val doc = Jsoup.parse(raceHtml)
    if (doc.select(".tab-x").isEmpty) {
      println(raceHtml)
      throw new InvalidDetailPage
    }
    val raceDetails = doc.select("title").text()
    def getGoingFromHeader(header: Elements) = {
      val goingElement = header.select("li:contains(going)")
      if (goingElement.size() == 0) "<Unknown>" else goingElement.text().replaceAll("Going: ", "")
    }

    def getSurfaceFromHeader(header: Elements) = {
      val surfaceElement = header.select("li:contains(surface)")
      if (surfaceElement.size() == 0) "<Unknown>" else surfaceElement.text().replaceAll("Surface: ", "")
    }

    def getRaceDetails: (String, String, String, String) = {
      val RaceDetails = """Racecard (\d+):(\d+) ([^|]*) \| ([^|]*) \| ([^|]*) \| .*""".r
      raceDetails match {
        case RaceDetails(hour, minute, track, _, raceName) => (hour, minute, track, raceName)
        case _ => {
          println(raceHtml)
          throw new MatchError
        }
      }
    }

    val header = getHeader(doc)
    val going = getGoingFromHeader(header)
    val surface = getSurfaceFromHeader(header)
    val (hour, minute, track, raceName) = getRaceDetails

    val builder = MongoDBObject.newBuilder
    builder += "name" -> raceName
    val distance: Option[Double] = getDistanceFromHeader(header).furlongs
    builder += "distance" -> (if (distance.isEmpty) 0 else distance.get)
    builder += "going" -> going
    builder += "surface" -> surface
    builder += "time" -> s"${hour}:${minute}"
    builder += "track" -> track

    val runners = doc.select("#racecard.tbl tbody tr").not(".disabled")

    builder += "runners" -> build(runners, MongoDBList.newBuilder, 0)
    builder.result()
  }
}
