package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.{DropLowest, NoDrop, RollSpec}
import org.specs2.Specification

import scala.collection.mutable

/**
  * Created by Doug on 14/11/2015.
  */
class RollSpecRunnerSpec extends Specification { def is = "Specification for a die roller".title ^ s2"""
  A RollSpecRunner can:         $p
    1. Execute a DiceSpec       ${execute}
  """

  def execute = {
    val statSpec = RollSpec(1, 4, 6, 0, Some(Range.inclusive(1, 2)), DropLowest(1))
    val roller = new RollSpecRunner with FixedDice { val fixed = mutable.Stack(3, 6, 4, 1, 2, 3) }

    roller.run(statSpec) must beLike {
      case Left(Result(Seq(Batch(rolls)))) =>
        rolls must contain(exactly(Roll(3, Dropped), Roll(6, Kept), Roll(4, Kept), Roll(1, Rerolled), Roll(2, Rerolled), Roll(3, Kept)))
    }
  }
}
