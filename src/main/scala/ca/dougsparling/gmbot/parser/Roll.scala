package ca.dougsparling.gmbot.parser

/**
  * Created by Doug on 14/11/2015.
  */
case class Roll(repeat: Int, number: Int, die: Int, modifier: Int, reroll: Option[Range], filter: Filter) {

}

sealed trait Filter { }
final case object NoFilter extends Filter
final case class Highest(n: Int) extends Filter
final case class Lowest(n: Int) extends Filter
