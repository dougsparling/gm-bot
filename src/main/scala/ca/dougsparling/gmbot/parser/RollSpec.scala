package ca.dougsparling.gmbot.parser

/**
  * Created by Doug on 14/11/2015.
  */
case class RollSpec(repeat: Int, number: Int, die: Int, modifier: Int, reroll: Option[Range], drop: Drop) {
  def shouldReroll(roll: Int) = reroll.exists(_.contains(roll))

  def toSpec: String =
    val repeatPart = if repeat == 1 then "" else s"$repeat times "
    val dicePart   = if number == 1 then s"d$die" else s"${number}d$die"
    val modPart    = if modifier > 0 then s"+$modifier" else if modifier < 0 then modifier.toString else ""
    val rerollPart = reroll.fold("") { r =>
      if r.size == 1 then s" reroll ${r.head}" else s" reroll ${r.head} to ${r.last}"
    }
    val dropPart   = drop match
      case NoDrop         => ""
      case DropLowest(1)  => " drop lowest"
      case DropLowest(n)  => s" drop lowest $n"
      case DropHighest(1) => " drop highest"
      case DropHighest(n) => s" drop highest $n"
    s"$repeatPart$dicePart$modPart$rerollPart$dropPart"

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
