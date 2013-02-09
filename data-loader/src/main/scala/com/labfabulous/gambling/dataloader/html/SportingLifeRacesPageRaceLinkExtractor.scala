package com.labfabulous.gambling.dataloader.html

import org.jsoup.Jsoup
import collection.JavaConversions._
import com.labfabulous.gambling.dataloader.SportingLife

class SportingLifeRacesPageRaceLinkExtractor extends LinksExtractor {

  def extract(html: String): List[String] = {
    val doc = Jsoup.parse(html)
    val raceUrlsElements = doc.select(".rac-cards").not(".disabled")

    def build(existing: List[String], index: Int): List[String] = {
      if (index == raceUrlsElements.length) {
        existing
      } else {
        val urlElement = raceUrlsElements.get(index)
        val url = urlElement.select(".ix.ixc a[href]")
        build(SportingLife.baseUrl + url.attr("href") :: existing, index + 1)
      }
    }

    build(List[String](), 0)
  }

}
