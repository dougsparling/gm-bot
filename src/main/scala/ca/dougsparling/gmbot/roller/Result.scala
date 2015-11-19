package ca.dougsparling.gmbot.roller

case class Result(batches: Seq[Batch])

case class Batch(rolls: Seq[Roll]) {
  def sum(mod: Int) = (rolls.filter(_.fate == Kept).map(_.n) ++ List(mod)).map(BigInt(_)).sum
}

case class Roll(n: Int, fate: Fate)

sealed trait Fate
case object Kept extends Fate
case object Rerolled extends Fate
case object Dropped extends Fate
