package com.labfabulous.gambling.dataloader.html

import com.mongodb.casbah.commons.Imports.DBObject
import com.labfabulous.gambling.dataloader.models.Distance
import org.jsoup.select.Elements
import org.jsoup.nodes.Document

abstract class DetailsExtractor {
  def extract(html: String): DBObject

  def getHeader(doc: Document) = {
    doc.select(".racecard-header .content-header li")
  }

  def getDistanceFromHeader(header: Elements) = {
    val Distance = """\((([^,]+),([^,]+)).*\).*|\((([^,]+))\).*""".r
    header.text() match {  // it could be in either place which is a bit funky
      case Distance(_,_,_,_:String,distance) => new Distance(distance)
      case Distance(_:String,_,distance,_,_) => new Distance(distance)
    }
  }
}
