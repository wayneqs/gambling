package com.labfabulous.gambling.dataloader

import html.SportingLifeRacesPageRaceLinksExtractor
import processors.RaceDayDownloader
import akka.actor.{Props, ActorSystem}
import com.labfabulous.{DayWorker, ProgressListener, Epocher}
import com.labfabulous.DayWorker.Start
import com.mongodb.casbah.MongoClient
import com.labfabulous.http.{Downloader, HttpThrottler}

object App {

  val system = ActorSystem("data-loader-system")

  def main(args: Array[String]) {

    val epochDate = Epocher.get()
    val listener = system.actorOf(Props(new ProgressListener), name="listener")

    val client: MongoClient = MongoClient()
    val db = client("racing_data")

    val linksExtractor = new SportingLifeRacesPageRaceLinksExtractor

    val downloader = Props(new Downloader(new HttpThrottler[String]("")))
    val downloadPath = s"${sys.env("HOME")}/var/gambling/downloads/web/"
    val racingDayProcessor = Props(new RaceDayDownloader(db, downloader, "http://www.sportinglife.com/racing/racecards", downloadPath, linksExtractor.extract))
    val cardsExtractor = system.actorOf(Props(new DayWorker(racingDayProcessor)), name="cards-extractor")
    cardsExtractor.tell(Start(epochDate), listener)
  }
}
