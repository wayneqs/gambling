package com.labfabulous.gambling.dataloader.models

class Weight( weight:String ) {
  val lbs = convertWeightToLbs

  def <(weight: Weight):Boolean = {
    lbs < weight.lbs
  }
  def <=(weight: Weight):Boolean = {
    lbs <= weight.lbs
  }
  def >(weight: Weight):Boolean = {
    lbs > weight.lbs
  }
  def >=(weight: Weight):Boolean = {
    lbs >= weight.lbs
  }
  def ==(weight: Weight):Boolean = {
    lbs == weight.lbs
  }

  def convertWeightToLbs: Int = {
    val Weight = """((\d+)-(\d+)).*|((\d+)).*""".r
    weight match {
      case Weight(_, _, _, _:String, stones) => stones.toInt * 14
      case Weight(_:String, stones, pounds, _, _) => (stones.toInt * 14) + pounds.toInt
      case _ => 0
    }
  }

}
