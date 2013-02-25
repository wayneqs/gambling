package com.labfabulous.gambling.dataloader

import html.{SportingLifeRacePageResultExtractor, SportingLifeRacePageCardExtractor, SportingLifeRacesPageRaceLinkExtractor}
import processors.{MeetingsPageProcessor, SportingLifeHorseMeetingProcessor, PlayerProcessor}
import akka.actor.{Props, ActorSystem}
import com.labfabulous.{DayWorker, TimeOutListener, Epocher}
import com.labfabulous.DayWorker.Start
import com.mongodb.casbah.MongoClient

object App {

  val system = ActorSystem("data-loader-system")

  def main(args: Array[String]) {

    val epochDate = Epocher.get()
    val listener = system.actorOf(Props(new TimeOutListener), name="listener")

    val raceCardProcessor = new SportingLifeHorseMeetingProcessor(MongoClient(), new SportingLifeRacePageCardExtractor)
    val meetingsPageProcessorProps = Props(new MeetingsPageProcessor(new SportingLifeRacesPageRaceLinkExtractor, raceCardProcessor))
    val cardsExtractor = system.actorOf(Props(new DayWorker(MongoClient(), meetingsPageProcessorProps)), name="cards-extractor")
    cardsExtractor.tell(Start("racecards", epochDate, "http://www.sportinglife.com/racing/racecards/"), listener)

    val resultProcessor = new SportingLifeHorseMeetingProcessor(MongoClient(), new SportingLifeRacePageResultExtractor)
    val resultsPageExtractorProps = Props(new MeetingsPageProcessor(new SportingLifeRacesPageRaceLinkExtractor, resultProcessor))
    val resultsExtractor = system.actorOf(Props(new DayWorker(MongoClient(), resultsPageExtractorProps)), name="results-extractor")
    resultsExtractor.tell(Start("results", epochDate, "http://www.sportinglife.com/racing/results/"), listener)

    val playerProcessorProps = Props(new PlayerProcessor(MongoClient(), new SportingLifeRacesPageRaceLinkExtractor))
    val playersExtractor = system.actorOf(Props(new DayWorker(MongoClient(), playerProcessorProps)), name="players-extractor")
    playersExtractor.tell(Start("players", epochDate, "http://www.sportinglife.com/racing/results/"), listener)
  }
}
