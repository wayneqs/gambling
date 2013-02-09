package com.labfabulous.gambling.dataloader.models

import com.mongodb.casbah.commons.MongoDBObject
import org.jsoup.nodes.Element
import com.labfabulous.gambling.dataloader.SportingLife

object Player {
  def getPlayerId(url: String) = {
    val UrlMatcher = """(.*/\d+)/.*""".r
    url match {
      case UrlMatcher(id) => id
      case _ => ""
    }
  }
  def getPlayer(field: Element) = {
    val playerAnchor = field.select("a[href]")
    val playerUrl: String = SportingLife.baseUrl + playerAnchor.attr("href")
    new Player(playerUrl, playerAnchor.text())
  }
}

class Player(val url: String, val name: String) {
  val globalId = Player.getPlayerId(url)
  val dbObject = MongoDBObject("globalId" -> globalId, "name" -> name, "url" -> url)
}
