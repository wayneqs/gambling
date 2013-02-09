package com.labfabulous.gambling.dataloader.processors

import com.labfabulous.Work
import com.labfabulous.DayWorker._
import org.joda.time.DateTime
import com.labfabulous.gambling.dataloader.WebClient
import com.labfabulous.gambling.dataloader.html.{InvalidDetailPage, LinksExtractor}
import com.mongodb.casbah.commons.MongoDBObject
import org.jsoup.select.Elements
import com.labfabulous.DayWorker.ErrorDie
import com.labfabulous.DayWorker.ErrorOK
import com.labfabulous.DayWorker.OK
import com.labfabulous.DayWorker.Start
import com.mongodb.casbah.MongoClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.labfabulous.gambling.dataloader.models.Player
import akka.actor.Actor

class PlayerProcessor(extractor: LinksExtractor) extends Work with Actor {
  var isOK = true
  private val dbCollection = MongoClient()("racing_data")("players")

  private def newObject (fieldName: String, fieldValue: String): Boolean = {
    val q = MongoDBObject(fieldName -> fieldValue)
    dbCollection.findOne(q) match {
      case None => true
      case _ => false
    }
  }

  def saveIfNotAlreadyInDatabase(element: Element) {
    val player = Player.getPlayer(element)
    if (player.globalId != "" && newObject("globalId", player.globalId)) {
      dbCollection += player.dbObject
    }
  }

  def savePlayersInRow(rows: Elements, index: Int) {
    if (index < rows.size()) {
      val fields: Elements = rows.get(index).select("td")
      saveIfNotAlreadyInDatabase(fields.get(2))
      saveIfNotAlreadyInDatabase(fields.get(3))
      saveIfNotAlreadyInDatabase(fields.get(6))
      savePlayersInRow(rows, index+1)
    }
  }

  def savePlayers(html: String) {
    val doc = Jsoup.parse(html)
    val rows = doc.select(".tab-x tbody tr").not(".disabled").not(".note")
    savePlayersInRow(rows, 0)
  }

  def doWork(msg: Start, date: DateTime) = {

    def processLink(link: String) {
      try {
        val response = WebClient.get(link)
        if (WebClient.isError(response)) {
          isOK &= false
        } else {
          if (response._1 == 200) {
            savePlayers(response._2)
          }
        }
      } catch {
        case x: InvalidDetailPage => {
          println(s"ERROR (weird html): ${link}: ${x.getMessage}")
          isOK &= false
        }
        case x: Throwable => {
          println(s"ERROR (unexpected): ${link}: ${x.getMessage}")
          isOK &= false
        }
      }
    }

    def processLinks(links: List[String]): Result = {
      links.foreach(link => processLink(link))
      isOK match {
        case true => OK(msg, date)
        case false => ErrorOK(msg, date, "link processing failed")
      }
    }

    val targetUrl = msg.baseUrl + date.toString("dd-MM-yyyy")
    WebClient.get(targetUrl) match {
      case (200, response) => {
        processLinks(extractor.extract(response))
      }
      case (404, response) => { OK(msg, date) } // the link doesn't go anywhere. this is annoying, but ok
      case (code:Int, response) => { ErrorDie(msg, date, response) }
    }
  }

  def receive = {
    case doWork: WorkForDate => {

    }
  }
}
