package ca.dougsparling.gmbot.roller

case class Result(batches: Seq[Batch])

case class Batch(rolls: Seq[Roll]) {
  def sum(mod: Int) = rolls.filter(_.fate == Kept).map(r => BigInt(r.n)).sum + mod
}

case class Roll(n: Long, fate: Fate)

sealed trait Fate
case object Kept extends Fate
case object Rerolled extends Fate
case object Dropped extends Fate
