package ca.dougsparling.gmbot.roller

case class Result(batches: Seq[Batch])

case class Batch(rolls: Seq[Roll])

case class Roll(n: Int, fate: Fate)

sealed trait Fate
case object Kept extends Fate
case object Rerolled extends Fate
case object Dropped extends Fate
