package com.labfabulous.gambling.dataloader.processors

import com.labfabulous.DayWorker._
import com.labfabulous.gambling.dataloader.WebClient
import com.labfabulous.gambling.dataloader.html.{InvalidDetailPage, LinksExtractor}
import com.mongodb.casbah.commons.MongoDBObject
import org.jsoup.select.Elements
import com.mongodb.casbah.MongoClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.labfabulous.gambling.dataloader.models.Player
import akka.actor.{OneForOneStrategy, Actor}
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}

class PlayerProcessor(mongo: MongoClient, extractor: LinksExtractor) extends Actor {
  var isOK = true
  private val dbCollection = mongo("racing_data")("players")

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

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

  def doWork(work: WorkForDate) {

    def processLink(link: String) {
      try {
        val response = WebClient.get(link)
        if (WebClient.isError(response)) {
          println(s"ERROR (problem getting link): ${link} code return '${response._1}'")
          isOK &= false
        } else {
          if (response._1 == 200) {
            savePlayers(response._2)
            sender ! Progress()
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

    def processLinks(links: List[String]) {
      links.foreach(link => processLink(link))
      isOK match {
        case true => sender ! WorkDone(work.start.category, work.date)
        case false => sender ! WorkFailed(work.start.category, work.date, "one of the links failed")
      }
    }

    val targetUrl = work.start.state + work.date.toString("dd-MM-yyyy")
    WebClient.get(targetUrl) match {
      case (200, response) => processLinks(extractor.extract(response))
      case (404, response) => sender ! WorkPartiallyDone(work.start.category, work.date, "${targetUrl} => 404")
      case (code: Int, response) => WorkFailed(work.start.category, work.date, "${targetUrl} => ${code} => ${response}")
    }
  }

  def receive = {
    case work: WorkForDate => doWork(work)
  }
}
