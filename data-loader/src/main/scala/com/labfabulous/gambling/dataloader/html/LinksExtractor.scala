package com.labfabulous.gambling.dataloader.html

abstract class LinksExtractor {
  def extract(html: String): List[String]
}
