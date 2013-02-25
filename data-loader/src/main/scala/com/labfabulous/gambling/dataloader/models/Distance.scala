package com.labfabulous.gambling.dataloader.models

class Distance(value:String) {
  val furlongs = convertToFurlongs(value.trim)

  private def convertToFurlongs(value:String) : Option[Double] = {
    val Dist = """((\d+)m\s*(\d+)f)|((\d+)m)|((\d+)f)|((\d+)m\s*(\d+)y)|((\d+)f\s*(\d+)y)|(^$)""".r
    value match {
      case Dist(_:String,miles,furlongs,_,_,_,_,_,_,_,_,_,_,_) => Some(furlongs.toDouble + miles.toDouble * 8)
      case Dist(_,_,_,_:String,miles,_,_,_,_,_,_,_,_,_) => Some(miles.toDouble * 8)
      case Dist(_,_,_,_,_,_:String,furlongs,_,_,_,_,_,_,_) => Some(furlongs.toDouble)
      case Dist(_,_,_,_,_,_,_,_:String,miles,yards,_,_,_,_) => Some(miles.toDouble * 8 + yards.toDouble * 0.00454545455)
      case Dist(_,_,_,_,_,_,_,_,_,_,_:String,furlongs,yards,_) => Some(furlongs.toDouble + yards.toDouble * 0.00454545455)
      case _ => None
    }
  }
}
