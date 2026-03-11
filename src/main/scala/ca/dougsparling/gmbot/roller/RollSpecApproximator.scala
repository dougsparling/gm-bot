package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.{DropHighest, DropLowest, NoDrop, RollSpec}

/**
  * For large rolls (many dice or many rerolls), simulating every roll is slow.
  * By the Central Limit Theorem, the sum of N independent dice converges to a
  * normal distribution with analytically computable mean and variance.
  */
trait RollSpecApproximator {
  protected def gaussian: Gaussian

  def approximate(spec: RollSpec): Result = {
    val kept = keptValues(spec)
    val (dieMean, dieVar) = stats(kept)
    val keptDice = spec.number - droppedCount(spec)
    val batchMean = keptDice * dieMean
    val batchStd  = math.sqrt(keptDice * dieVar)

    val batches = (1 to spec.repeat).map { _ =>
      val sample = math.round(batchMean + batchStd * gaussian.nextGaussian()).toInt
      Batch(List(Roll(sample, Kept)))
    }
    Result(batches)
  }

  private def keptValues(spec: RollSpec): IndexedSeq[Int] = {
    val all = 1 to spec.die
    spec.reroll.fold(all: IndexedSeq[Int])(r => all.filterNot(r.contains))
  }

  private def stats(kept: IndexedSeq[Int]): (Double, Double) = {
    val n    = kept.size.toDouble
    val mean = kept.sum / n
    val variance = kept.map(x => (x - mean) * (x - mean)).sum / n
    (mean, variance)
  }

  private def droppedCount(spec: RollSpec): Int = spec.drop match {
    case NoDrop         => 0
    case DropHighest(n) => n
    case DropLowest(n)  => n
  }
}
