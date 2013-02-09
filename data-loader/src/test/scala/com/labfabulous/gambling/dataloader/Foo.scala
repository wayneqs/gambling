package com.labfabulous.gambling.dataloader

import org.specs2.mutable.Specification

class Foo extends Specification {

  "do it" should {
    "goo" in {
//      val s2 = "(4yo+, 1m, Class 7, 14 runners) Winner $4,956 Surface: Dirt"
//      val Distance(_:String,_,distance2,_,_) = s2
//      println(distance2)

      val Distance = """\((([^,]+),([^,]+)).*\).*|\((([^,]+))\).*""".r
      val s1 = "(6f) Winner $4,956 Surface: Dirt"
      val Distance(_,_,_,_:String,distance1) = s1
      println(distance1)
    }
  }

}
