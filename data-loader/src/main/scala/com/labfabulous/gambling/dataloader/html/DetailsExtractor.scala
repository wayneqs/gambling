package com.labfabulous.gambling.dataloader.html

import com.mongodb.casbah.commons.Imports.DBObject

abstract class DetailsExtractor {
  def extract(html: String): DBObject
}
