package com.labfabulous.gambling.dataloader.html

import org.jsoup.Jsoup
import org.jsoup.select.Elements
import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject, MongoDBListBuilder}
import com.mongodb.casbah.commons.Imports.DBObject
import org.jsoup.nodes.Element
import com.labfabulous.gambling.dataloader.models.{Player, Weight, Odds}

class SportingLifeRacePageResultExtractor extends DetailsExtractor {

  def extractResult(elements: Elements, distance: Option[Double], winningTime: Option[Double], index: Int): (Option[Double], DBObject) = {
    val fields: Elements = elements.get(index).select("td")
    val position = getPosition(fields.get(0))
    val DistanceField = 1

    def calculateSeparation(index: Int): Option[Double] = {
      def frac2Length(frac: String) = frac match {
        case "¼" => 0.25
        case "½" => 0.5
        case "¾" => 0.75
        case _ => 1.0
      }
      if (elements.size() <= index) {
        None
      } else {
        val space = elements.get(index).select("td").get(DistanceField).text()
        val ft2Furlong = 0.00151515152;
        val lengths2Ft = 8;
        space.trim match {
          case "" =>  Some(0.0)
          case "nse" => Some(0.5 * ft2Furlong)
          case "nk" => Some(4 * ft2Furlong)
          case "s.h" => Some(2 * ft2Furlong)
          case d:String => {
            val Distance = """((\d+))*((.))*""".r
            d match {
              case Distance(_:String,whole,_:String,frac) => Some(whole.toDouble * lengths2Ft * ft2Furlong + frac2Length(frac) * lengths2Ft * ft2Furlong)
              case Distance(_:String,whole,_,_) => Some(whole.toDouble * lengths2Ft * ft2Furlong)
              case Distance(_,_,_:String,frac) => Some(frac2Length(frac) * lengths2Ft * ft2Furlong)
              case _ => None
            }
          }
        }
      }
    }
    def calcVelocity: Option[Double] = {
      if (winningTime.isEmpty || distance.isEmpty) {
        None // no velocity calculations possible
      } else {
        Some(distance.get / winningTime.get)
      }
    }
    val builder = MongoDBObject.newBuilder
    builder += "position" -> (if (position.isEmpty) "" else position.get)
    builder += "distance" -> fields.get(DistanceField).text()
    builder += "velocity" -> (if (position.isEmpty) 0 else calcVelocity)
    builder += "horse" -> Player.getPlayer(fields.get(2)).dbObject
    builder += "trainer" -> Player.getPlayer(fields.get(3)).dbObject
    builder += "age" -> fields.get(4).text()
    builder += "weight" -> new Weight(fields.get(5).text()).lbs
    builder += "jockey" -> Player.getPlayer(fields.get(6)).dbObject
    builder += "odds" -> new Odds(fields.get(7).text()).frac
    (calculateSeparation(index+1), builder.result())
  }

  def build (rows: Elements, distance: Option[Double], winningTime: Option[Double], builder: MongoDBListBuilder, index: Int): MongoDBList = {
    if (index == rows.size) {
      builder.result()
    } else {
      val (separation, dbObject) = extractResult(rows, distance, winningTime, index)
      builder += dbObject
      build(rows, if (separation.isEmpty||distance.isEmpty) None else Some(distance.get - separation.get), winningTime, builder, index+1)
    }
  }

  def extract(html: String): DBObject = {
    def getWinningTime(element: Element) = {
      def extractWinningTime: Option[Double] = {
        val Time = """Winning time:\s*((\d+)m)?\s?(((\d+)\.)?(\d+)s).*""".r
        element.text match {
          case Time(_: String, minutes, _: String, _: String, seconds, point) => Some(60 * minutes.toDouble + seconds.toDouble + 1 * (point.toDouble / 100))
          case Time(_, _, _: String, _: String, seconds, point) => Some(seconds.toDouble + 1 * (point.toDouble / 100))
          case Time(_: String, minutes, _: String, _, _, seconds) => Some(60 * minutes.toDouble + seconds.toDouble)
          case _ => None
        }
      }
      if (element == null) None else extractWinningTime
    }
    val doc = Jsoup.parse(html)
    val header = getHeader(doc)
    val distance = getDistanceFromHeader(header).furlongs
    val rows = doc.select(".tab-x tbody tr").not(".disabled").not(".note")
    val winningTime = getWinningTime(doc.select(".racecard-status ul li").first())
    val builder = MongoDBObject.newBuilder
    builder += "runners" -> build(rows, distance, winningTime, MongoDBList.newBuilder, 0)
    builder.result()
  }

    private def getPosition(field: Element) = {
      val Position = """\s*(\d+)\s*<.*""".r
      field.html() match {
        case Position(position) => Some(position)
        case _ => None // maybe log out some stuff
      }
    }
}
