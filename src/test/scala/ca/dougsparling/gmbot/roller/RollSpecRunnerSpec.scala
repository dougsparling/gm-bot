package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.{DropHighest, DropLowest, NoDrop, RollSpec}
import org.specs2.Specification
import org.specs2.matcher.Matcher

import scala.collection.mutable

/**
  * Created by Doug on 14/11/2015.
  */
class RollSpecRunnerSpec extends Specification { def is = "Specification for a die roller".title ^ s2"""
  A RollSpecRunner can:         $p
    1. Execute a DiceSpec       $execute
    2. Catch invalid DiceSpecs  $invalid
  """

  def invalid =                                   p^s2"""
    A RollSpec is rejected when:                  $p
      1. More dice are dropped than rolled (high)   ${specs().dropHighest}
      2. More dice are dropped than rolled (low)    ${specs().dropLowest}
      3. All dice would be rerolled                 ${specs().rerolls}
  """

  def execute = {
    val statSpec = RollSpec(1, 4, 6, 0, Some(Range.inclusive(1, 2)), DropLowest(1))
    val roller = new RollSpecRunner with FixedDice { val fixed = mutable.Stack(3, 6, 4, 1, 2, 3) }

    roller.run(statSpec) must beLike {
      case Left(Result(Seq(Batch(rolls)))) =>
        rolls must contain(exactly(Roll(3, Dropped), Roll(6, Kept), Roll(4, Kept), Roll(1, Rerolled), Roll(2, Rerolled), Roll(3, Kept)))
    }
  }

  case class specs() {
    val roller = new RollSpecRunner with NoDice

    def dropLowest = roller.run(RollSpec(1, 10, 6, 0, None, DropLowest(11))) must beFailedRoll("Can't drop more dice than are rolled")

    def dropHighest = roller.run(RollSpec(1, 10, 6, 0, None, DropHighest(11))) must beFailedRoll("Can't drop more dice than are rolled")

    def rerolls = roller.run(RollSpec(1, 1, 10, 0, Some(Range.inclusive(1,10)), NoDrop)) must beFailedRoll("All dice would be rerolled")

    def beFailedRoll(expected: String): Matcher[Either[Result, String]] = beLike {
      case Right(actual: String) => actual must_== expected
    }
  }

}
