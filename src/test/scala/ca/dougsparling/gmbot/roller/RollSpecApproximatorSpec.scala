package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.{DropHighest, DropLowest, NoDrop, RollSpec}
import org.specs2.Specification

import scala.collection.mutable

class RollSpecApproximatorSpec extends Specification { def is = "for a roll approximator".title ^ s2"""
  A RollSpecApproximator can:             $p
    1. Approximate a large dice pool      $approxBasic
    2. Apply a modifier                   $approxModifier
    3. Account for rerolled faces         $approxRerolls
    4. Produce multiple repeat batches    $approxRepeats
    5. Account for dropped dice           $approxDrop
  """

  // With gaussian=0, sample = round(batchMean), giving exact deterministic results.
  def approximator(gaussians: Double*) = new RollSpecApproximator with FixedGaussian {
    val fixedGaussians = mutable.Stack(gaussians*)
  }

  // 9999d6: mean=3.5, batchMean=34996.5, round -> 34997
  def approxBasic = {
    val Result(Seq(batch)) = approximator(0.0).approximate(RollSpec(1, 9999, 6, 0, None, NoDrop))
    batch.sum(0) must_== BigInt(34997)
  }

  // same dice pool, +5 modifier
  def approxModifier = {
    val Result(Seq(batch)) = approximator(0.0).approximate(RollSpec(1, 9999, 6, 5, None, NoDrop))
    batch.sum(5) must_== BigInt(35002)
  }

  // 1d6 reroll 1 to 5: only face 6 kept, mean=6, var=0, sample=6
  def approxRerolls = {
    val Result(Seq(batch)) = approximator(0.0).approximate(RollSpec(1, 1, 6, 0, Some(Range.inclusive(1, 5)), NoDrop))
    batch.sum(0) must_== BigInt(6)
  }

  // 2 times 9999d6: two batches each producing 34997
  def approxRepeats = {
    val Result(Seq(b1, b2)) = approximator(0.0, 0.0).approximate(RollSpec(2, 9999, 6, 0, None, NoDrop))
    (b1.sum(0) must_== BigInt(34997)) and (b2.sum(0) must_== BigInt(34997))
  }

  // 9999d6 drop highest 1: keptDice=9998, batchMean=34993.0, sample=34993
  def approxDrop = {
    val Result(Seq(batch)) = approximator(0.0).approximate(RollSpec(1, 9999, 6, 0, None, DropHighest(1)))
    batch.sum(0) must_== BigInt(34993)
  }
}
