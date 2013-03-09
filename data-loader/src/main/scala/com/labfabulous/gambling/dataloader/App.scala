package com.labfabulous.gambling.dataloader

import html.SportingLifeRacesPageRaceLinksExtractor
import processors.HistoricalRaceDayDownloader
import akka.actor.{Props, ActorSystem}
import com.labfabulous._
import com.mongodb.casbah.MongoClient
import com.labfabulous.http.HttpThrottler
import com.labfabulous.DayWorker.Start
import com.labfabulous.FileSystemCleaner.Clean

object App {

  val system = ActorSystem("data-loader-system")

  def main(args: Array[String]) {

    val epochDate = Epocher.get()
    val listener = system.actorOf(Props(new ProgressListener), name="listener")

    val client: MongoClient = MongoClient()
    val db = client("racing_data")
    val downloadIndex = db("downloadIndex")
    val linksExtractor = new SportingLifeRacesPageRaceLinksExtractor

    val downloadPath = s"${sys.env("HOME")}/var/gambling/downloads/web/"
    val filesystemWriter = new FileCreator

    val racecardDownloader = Props(new HistoricalRaceDayDownloader(downloadIndex,
                                  new HttpThrottler[String](""),
                                  "http://www.sportinglife.com/racing/racecards",
                                  downloadPath,
                                  filesystemWriter,
                                  linksExtractor.extract))
    val cardsWorker = system.actorOf(Props(new DayWorker(racecardDownloader)), name="cards-worker")
    cardsWorker.tell(Start(epochDate), listener)

    val resultDownloader = Props(new HistoricalRaceDayDownloader(downloadIndex,
                                                      new HttpThrottler[String](""),
                                                      "http://www.sportinglife.com/racing/results",
                                                      downloadPath,
                                                      filesystemWriter,
                                                      linksExtractor.extract))
    val resultsWorker = system.actorOf(Props(new DayWorker(resultDownloader)), name="results-worker")
    resultsWorker.tell(Start(epochDate), listener)

//    val isIndexed = new FileIsIndexed(downloadIndex)
//    val fileSystemCleaner = system.actorOf(Props(new FileSystemCleaner(downloadPath, isIndexed.checkFalse)), "filesystem-cleaner")
//    fileSystemCleaner.tell(Clean(), listener)
  }
}
