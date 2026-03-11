package ca.dougsparling.gmbot.parser

/**
  * Created by Doug on 14/11/2015.
  */
case class RollSpec(repeat: Int, number: Int, die: Int, modifier: Int, reroll: Option[Range], drop: Drop) {
  def shouldReroll(roll: Int) = reroll.exists(_.contains(roll))

  def shouldApproximate: Boolean = {
    val rerollCount = reroll.fold(0)(_.size)
    val keptFaces = die - rerollCount
    if (keptFaces <= 0) return true
    val expectedRollsPerDie = die.toDouble / keptFaces
    number.toLong * repeat * expectedRollsPerDie > RollSpec.SimulationThreshold
  }
}

object RollSpec {
  private val SimulationThreshold = 10000.0
}

sealed trait Drop
case object NoDrop extends Drop
final case class DropHighest(n: Int) extends Drop
final case class DropLowest(n: Int) extends Drop
