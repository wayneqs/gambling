package com.labfabulous.gambling.dataloader.models

class Odds(value:String) {
  val frac = convertOddsToFractional

  def <(odds: Odds):Boolean = {
    frac < odds.frac
  }
  def <=(odds: Odds):Boolean = {
    frac <= odds.frac
  }
  def >(odds: Odds):Boolean = {
    frac > odds.frac
  }
  def >=(odds: Odds):Boolean = {
    frac >= odds.frac
  }
  def ==(odds: Odds):Boolean = {
    frac == odds.frac
  }

  def convertOddsToFractional: Double = {
    val Odds = """((\d+)/(\d+)).*|((\d+)).*""".r
    value match {
      case Odds(_:String, numerator, denominator, _, _) => numerator.toDouble / denominator.toDouble
      case Odds(_, _, _, _:String, number) => number.toDouble
      case _ => 0

    }
  }
}
