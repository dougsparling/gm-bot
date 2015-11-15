package ca.dougsparling.gmbot.parser

/**
  * Created by Doug on 14/11/2015.
  */
case class RollSpec(repeat: Int, number: Int, die: Int, modifier: Int, reroll: Option[Range], drop: Drop) {
  def shouldReroll(roll: Int) = reroll.exists(_.contains(roll))
}

sealed trait Drop
case object NoDrop extends Drop
final case class DropHighest(n: Int) extends Drop
final case class DropLowest(n: Int) extends Drop
