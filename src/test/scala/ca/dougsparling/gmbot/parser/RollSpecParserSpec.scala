package ca.dougsparling.gmbot.parser

import org.specs2.Specification
import org.specs2.matcher.{Expectable, Matcher}

class RollSpecParserSpec extends Specification { def is = "Specification for a die roll parser".title ^ s2"""
  A RollParser can accept:         $p
    1. A die roll           $aDieRoll
    2. Multiple die rolls   $multiRoll
    3. Modified die rolls   $modMultiRoll
    4. Repeated die rolls   $repeatRoll
    5. Rerolls              $reroll
    6. Drop highest/lowest  $dropDice
    7. Everything together! $everything
  """

  def aDieRoll = s2"""
    A die roll should
      1. Succeed for normal die    ${die().d6}
      2. Fail for zero die         ${die().d0}
      3. Fail for too large die    ${die().dTooMuch}
    """

  def multiRoll = s2"""
    A multi roll should
      1. Succeed for normal rolls  ${multi()._3d6}
      2. Fail for zero dice        ${multi()._0d6}
      3. Fail for zero die         ${multi()._3d0}
      4. Fail for too many dice    ${multi()._billiond6}
      5. Fail for too large die    ${multi()._1dTooMany}
    """

  def modMultiRoll = s2"""
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

  def repeatRoll = s2"""
    A repeated roll should
      1. Succeed for repetitions specified by x      ${rep()._4x}
      2. Succeed for repetitions specified by times  ${rep()._4times}
      3. Fail for zero repetitions                   ${rep()._0x}
    """

  def reroll = s2"""
    A reroll should
      1. Succeed for some face                       ${rr().rr6}
      2. Fail for rerolling zeroes                   ${rr().rr0}
      3. Succeed for a range of faces                ${rr().rr56}
      4. Succeed for a reversed range of faces       ${rr().rr65}
    """

  def dropDice = s2"""
    A drop should
      1. Succeed for highest die                     ${drop().high}
      2. Succeed for multiple highest                ${drop().manyHigh}
      3. Succeed for lowest die                      ${drop().low}
      4. Succeed for multiple lowest                 ${drop().manyLow}
      5. Fail to drop nothing (high)                 ${drop().highZero}
      6. Fail to drop nothing (low)                  ${drop().lowZero}
    """

  def everything = parse("6 times 5d6 + 1 reroll 1 to 2 drop lowest 2") must succeedWith(RollSpec(6, 5, 6, 1, Some(1 to 2), DropLowest(2)))

  def rollPrefix = s2"""
    The optional "roll" prefix should
      1. Be accepted before a roll  ${parse("roll d6") must succeedWith(RollSpec(1, 1, 6, 0, None, NoDrop))}
      2. Not be required            ${parse("d6") must succeedWith(RollSpec(1, 1, 6, 0, None, NoDrop))}
    """

  def parse(s: String) = RollSpecParser.parseAll(RollSpecParser.roll, s)

  def succeedWith(expected: RollSpec): Matcher[RollSpecParser.ParseResult[RollSpec]] =
    (result: RollSpecParser.ParseResult[RollSpec]) => result match {
      case RollSpecParser.Success(actual, _) => (actual == expected, s"got $actual, expected $expected", s"unexpectedly got $actual")
      case f => (false, "", s"parse failed: $f")
    }

  def failParsing: Matcher[RollSpecParser.ParseResult[RollSpec]] =
    (result: RollSpecParser.ParseResult[RollSpec]) => result match {
      case _: RollSpecParser.NoSuccess => (true, "parse failed as expected", "")
      case RollSpecParser.Success(r, _) => (false, "", s"expected parse failure but got: $r")
    }

  case class die() {
    def d6       = parse("d6") must succeedWith(RollSpec(1, 1, 6, 0, None, NoDrop))
    def d0       = parse("d0") must failParsing
    def dTooMuch = parse("d9999999999") must failParsing
  }

  case class multi() {
    def _3d6       = parse("3d6") must succeedWith(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _0d6       = parse("0d6") must failParsing
    def _3d0       = parse("3d0") must failParsing
    def _billiond6 = parse("1000000000d6") must failParsing
    def _1dTooMany = parse("1d9999999999") must failParsing
  }

  case class mod() {
    def _3d6p5   = parse("3d6+5") must succeedWith(RollSpec(1, 3, 6, 5, None, NoDrop))
    def _3d6p5sp = parse("3d6 + 5") must succeedWith(RollSpec(1, 3, 6, 5, None, NoDrop))
    def _3d6p0   = parse("3d6+0") must succeedWith(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _3d6p0sp = parse("3d6 + 0") must succeedWith(RollSpec(1, 3, 6, 0, None, NoDrop))
    def _3d6m5   = parse("3d6-5") must succeedWith(RollSpec(1, 3, 6, -5, None, NoDrop))
    def _3d6m5sp = parse("3d6 - 5") must succeedWith(RollSpec(1, 3, 6, -5, None, NoDrop))
    def _3d6pLots = parse("3d6 + 9999999999") must failParsing
    def _3d6mLots = parse("3d6 - 9999999999") must failParsing
  }

  case class rep() {
    def _4x     = parse("4x3d6+5") must succeedWith(RollSpec(4, 3, 6, 5, None, NoDrop))
    def _4times = parse("4 times 3d6+5") must succeedWith(RollSpec(4, 3, 6, 5, None, NoDrop))
    def _0x     = parse("0x3d6+5") must failParsing
  }

  case class rr() {
    def rr6  = parse("5x3d6+5 reroll 6") must succeedWith(RollSpec(5, 3, 6, 5, Some(6 to 6), NoDrop))
    def rr0  = parse("5x3d6+5 reroll 0") must failParsing
    def rr56 = parse("5x3d6+5 reroll 5 to 6") must succeedWith(RollSpec(5, 3, 6, 5, Some(5 to 6), NoDrop))
    def rr65 = parse("5x3d6+5 reroll 6 to 5") must succeedWith(RollSpec(5, 3, 6, 5, Some(5 to 6), NoDrop))
  }

  case class drop() {
    def high     = parse("4d6 drop highest") must succeedWith(RollSpec(1, 4, 6, 0, None, DropHighest(1)))
    def manyHigh = parse("5d6 drop highest 2") must succeedWith(RollSpec(1, 5, 6, 0, None, DropHighest(2)))
    def low      = parse("4d6 drop lowest") must succeedWith(RollSpec(1, 4, 6, 0, None, DropLowest(1)))
    def manyLow  = parse("5d6 drop lowest 2") must succeedWith(RollSpec(1, 5, 6, 0, None, DropLowest(2)))
    def highZero = parse("5d6 drop highest 0") must failParsing
    def lowZero  = parse("5d6 drop lowest 0") must failParsing
  }
}
