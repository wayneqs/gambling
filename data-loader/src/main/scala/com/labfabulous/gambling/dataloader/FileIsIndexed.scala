package com.labfabulous.gambling.dataloader

import com.mongodb.casbah.MongoCollection
import java.nio.file.Path
import com.mongodb.casbah.commons.MongoDBObject

class FileIsIndexed(collection: MongoCollection) {
  def checkFalse(path: Path): Boolean = {
    val q = MongoDBObject("location" -> path.toString)
    collection.findOne(q) match {
      case None => true
      case _ => false
    }
  }
}
