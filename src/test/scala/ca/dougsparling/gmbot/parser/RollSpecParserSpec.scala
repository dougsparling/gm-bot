package ca.dougsparling.gmbot.parser

import org.specs2.Specification
import org.specs2.matcher.ParserMatchers

/**
  * Created by Doug on 14/11/2015.
  */
class RollSpecParserSpec extends Specification with ParserMatchers { def is = "Specification for a die roll parser".title ^ s2"""
  A RollParser can accept:         $p
    1. A die roll           $aDieRoll
    2. Multiple die rolls   $multiRoll
    3. Modified die rolls   $modMultiRoll
    4. Repeated die rolls   $repeatRoll
    5. Rerolls              $reroll
    6. Drop highest/lowest  $dropDice
    7. Everything together! $everything

  """

  def aDieRoll =                   p^s2"""
    A die roll should
      1. Succeed for normal die    ${die().d6}
      2. Fail for zero die         ${die().d0}
      3. Fail for too large die    ${die().dTooMuch}
    """

  def multiRoll =                  p^s2"""
    A multi roll should
      1. Succeed for normal rolls  ${multi()._3d6}
      2. Fail for zero dice        ${multi()._0d6}
      3. Fail for zero die         ${multi()._3d0}
      4. Fail for too many dice    ${multi()._10000d6}
      5. Fail for too large die    ${multi()._1dTooMany}
    """

  def modMultiRoll =                                  p^s2"""
    A modified multi roll should
      1. Succeed for positive mods                    ${mod()._3d6p5}
      2. Succeed for positive mods with padding       ${mod()._3d6p5sp}
      3. Succeed for zero modifier                    ${mod()._3d6p0}
      4. Succeed for zero modifier with padding       ${mod()._3d6p0sp}
      5. Succeed for negative mods                    ${mod()._3d6m5}
      6. Succeed for negative mods with padding       ${mod()._3d6m5sp}
      7. Fail for large magnitude modifier (pos)      ${mod()._3d6pLots}
      8. Fail for large magnitude modifier (neg)      ${mod()._3d6mLots}
    """

  def repeatRoll =                                 p^s2"""
    A repeated roll should
      1. Succeed for repetitions specified by x      ${rep()._4x}
      2. Succeed for repetitions specified by times  ${rep()._4times}
      3. Fail for zero repetitions                   ${rep()._0x}
    """

  def reroll =                                       p^s2"""
    A reroll should
      1. Succeed for some face                       ${rr().rr6}
      2. Fail for rerolling zeroes                   ${rr().rr0}
      3. Succeed for a range of faces                ${rr().rr56}
      4. Succeed for a reversed range of faces       ${rr().rr65}
    """

  def dropDice =                                     p^s2"""
    A drop should
      1. Succeed for highest die                     ${drop().high}
      2. Succeed for multiple highest                ${drop().manyHigh}
      3. Succeed for lowest die                      ${drop().low}
      4. Succeed for multiple lowest                 ${drop().manyLow}
      5. Fail to drop nothing (high)                 ${drop().highZero}
      6. Fail to drop nothing (low)                  ${drop().lowZero}
    """

  def everything = RollSpecParser.roll must succeedOn("6 times 5d6 + 1 reroll 1 to 2 drop lowest 2").withResult(RollSpec(6, 5, 6, 1, Some(1 to 2), DropLowest(2)))

  override val parsers = RollSpecParser

  case class die() {
    def d6       = RollSpecParser.roll must succeedOn("d6").withResult(RollSpec(1, 1, 6, 0, None, NoDrop))
    def d0       = RollSpecParser.roll must failOn("d0")
    def dTooMuch = RollSpecParser.roll must failOn("d9999999999")
  }

  case class multi() {
    def _3d6       = RollSpecParser.roll must succeedOn("3d6").withResult(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _0d6       = RollSpecParser.roll must failOn("0d6")
    def _3d0       = RollSpecParser.roll must failOn("3d0")
    def _10000d6   = RollSpecParser.roll must failOn("10000d6")
    def _1dTooMany = RollSpecParser.roll must failOn("1d9999999999")
  }

  case class mod() {
    def _3d6p5 = RollSpecParser.roll must succeedOn("3d6+5").withResult(RollSpec(1, 3, 6, 5, None, NoDrop))
    def _3d6p5sp = RollSpecParser.roll must succeedOn("3d6 + 5").withResult(RollSpec(1, 3, 6, 5, None, NoDrop))
    def _3d6p0 = RollSpecParser.roll must succeedOn("3d6+0").withResult(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _3d6p0sp = RollSpecParser.roll must succeedOn("3d6 + 0").withResult(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _3d6m5 = RollSpecParser.roll must succeedOn("3d6-5").withResult(RollSpec(1, 3, 6, -5, None, NoDrop))
    def _3d6m5sp = RollSpecParser.roll must succeedOn("3d6 - 5").withResult(RollSpec(1, 3, 6, -5, None, NoDrop))
    def _3d6pLots = RollSpecParser.roll must failOn("3d6 + 9999999999")
    def _3d6mLots = RollSpecParser.roll must failOn("3d6 - 9999999999")
  }

  case class rep() {
    def _4x = RollSpecParser.roll must succeedOn("4x3d6+5").withResult(RollSpec(4, 3, 6, 5, None, NoDrop))
    def _4times = RollSpecParser.roll must succeedOn("4 times 3d6+5").withResult(RollSpec(4, 3, 6, 5, None, NoDrop))
    def _0x = RollSpecParser.roll must failOn("0x3d6+5")
  }

  case class rr() {
    def rr6 = RollSpecParser.roll must succeedOn("5x3d6+5 reroll 6").withResult(RollSpec(5, 3, 6, 5, Some(6 to 6), NoDrop))
    def rr0 = RollSpecParser.roll must failOn("5x3d6+5 reroll 0")
    def rr56 = RollSpecParser.roll must succeedOn("5x3d6+5 reroll 5 to 6").withResult(RollSpec(5, 3, 6, 5, Some(5 to 6), NoDrop))
    def rr65 = RollSpecParser.roll must succeedOn("5x3d6+5 reroll 6 to 5").withResult(RollSpec(5, 3, 6, 5, Some(5 to 6), NoDrop))
  }

  case class drop() {
    def high = RollSpecParser.roll must succeedOn("4d6 drop highest").withResult(RollSpec(1, 4, 6, 0, None, DropHighest(1)))
    def manyHigh = RollSpecParser.roll must succeedOn("5d6 drop highest 2").withResult(RollSpec(1, 5, 6, 0, None, DropHighest(2)))
    def low = RollSpecParser.roll must succeedOn("4d6 drop lowest").withResult(RollSpec(1, 4, 6, 0, None, DropLowest(1)))
    def manyLow = RollSpecParser.roll must succeedOn("5d6 drop lowest 2").withResult(RollSpec(1, 5, 6, 0, None, DropLowest(2)))
    def highZero = RollSpecParser.roll must failOn("5d6 drop highest 0")
    def lowZero = RollSpecParser.roll must failOn("5d6 drop lowest 0")
  }
}
