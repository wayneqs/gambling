package com.labfabulous.gambling.dataloader.html

import org.jsoup.Jsoup
import org.jsoup.select.Elements
import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject, MongoDBListBuilder}
import com.mongodb.casbah.commons.Imports.DBObject
import org.jsoup.nodes.Element
import com.labfabulous.gambling.dataloader.models.{Player, Weight, Odds}

class SportingLifeRacePageResultExtractor extends DetailsExtractor {

  def extractResult(elements: Elements, index: Int): DBObject = {
    val builder = MongoDBObject.newBuilder
    val fields: Elements = elements.get(index).select("td")
    builder += "position" -> getPosition(fields.get(0))
    builder += "distance" -> fields.get(1).text()
    builder += "horse" -> Player.getPlayer(fields.get(2))
    builder += "trainer" -> Player.getPlayer(fields.get(3))
    builder += "age" -> fields.get(4).text()
    builder += "weight" -> new Weight(fields.get(5).text()).lbs
    builder += "jockey" -> Player.getPlayer(fields.get(6))
    builder += "odds" -> new Odds(fields.get(7).text()).frac
    builder.result()
  }

  def build (rows: Elements, builder: MongoDBListBuilder, index: Int): MongoDBList = {
    if (index == rows.size) {
      builder.result()
    } else {
      builder += extractResult(rows, index)
      build(rows, builder, index+1)
    }
  }

  def extract(html: String): DBObject = {
    val doc = Jsoup.parse(html)
    val rows = doc.select(".tab-x tbody tr").not(".disabled").not(".note")
    val builder = MongoDBObject.newBuilder
    builder += "runners" -> build(rows, MongoDBList.newBuilder, 0)
    builder.result()
  }

    private def getPosition(field: Element) = {
      val Position = """\s*(\d+)\s*<.*""".r
      field.html() match {
        case Position(position) => position
        case _ => "" // maybe log out some stuff
      }
    }
}
