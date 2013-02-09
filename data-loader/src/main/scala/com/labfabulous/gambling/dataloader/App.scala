package com.labfabulous.gambling.dataloader

import com.labfabulous.gambling.dataloader.RaceDataProtocol._
import html.{SportingLifeRacePageResultExtractor, SportingLifeRacePageCardExtractor, SportingLifeRacesPageRaceLinkExtractor}
import processors.{PlayerProcessor, MeetingsPageProcessor, SportingLifeHorseMeetingProcessor}
import akka.actor.{Props, ActorSystem}
import com.labfabulous.{DayWorker, Listener, Epocher}
import com.labfabulous.DayWorker.Start

object App {
  val system = ActorSystem("data-loader-system")

  def main(args: Array[String]) {
    val epochDate = Epocher.get()
    val listener = system.actorOf(Props[Listener], name="listener")

//    val raceCardProcessor = new SportingLifeHorseMeetingProcessor(new SportingLifeRacePageCardExtractor)
//    val cards = system.actorOf(Props(new MeetingsPageProcessor(new SportingLifeRacesPageRaceLinkExtractor, raceCardProcessor)), name = "race-cards-actor")
//    cards.tell(Start("http://www.sportinglife.com/racing/racecards/", "racecards", epochDate), listener)
//
//    val resultProcessor = new SportingLifeHorseMeetingProcessor(new SportingLifeRacePageResultExtractor)
//    val results = system.actorOf(Props(new MeetingsPageProcessor(new SportingLifeRacesPageRaceLinkExtractor, resultProcessor)), name = "results-actor")
//    results.tell(Start("http://www.sportinglife.com/racing/results/", "results", epochDate), listener)

    val players = system.actorOf(Props(new DayWorker(new PlayerProcessor(new SportingLifeRacesPageRaceLinkExtractor))), name="players-extractor")
    players.tell(Start("http://www.sportinglife.com/racing/results/", "players", epochDate), listener)
  }
}
