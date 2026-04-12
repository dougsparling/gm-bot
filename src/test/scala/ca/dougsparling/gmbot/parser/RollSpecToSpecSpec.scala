package ca.dougsparling.gmbot.parser

import org.specs2.Specification

class RollSpecToSpecSpec extends Specification { def is = "RollSpec.toSpec".title ^ s2"""
  RollSpec.toSpec produces parser-friendly strings:  $p
    basic die                   $basic
    multiple dice               $multipleDice
    positive modifier           $posMod
    negative modifier           $negMod
    repeat                      $repeat
    reroll single face          $rerollSingle
    reroll range                $rerollRange
    drop lowest                 $dropLowest
    drop lowest N               $dropLowestN
    drop highest                $dropHighest
    full ability check spec     $fullAbility
    round-trips through parser  $roundTrip
  """

  def basic       = RollSpec(1, 1, 6,  0, None, NoDrop).toSpec           must_== "d6"
  def multipleDice= RollSpec(1, 4, 6,  0, None, NoDrop).toSpec           must_== "4d6"
  def posMod      = RollSpec(1, 1, 20, 5, None, NoDrop).toSpec           must_== "d20+5"
  def negMod      = RollSpec(1, 1, 20,-2, None, NoDrop).toSpec           must_== "d20-2"
  def repeat      = RollSpec(3, 1, 6,  0, None, NoDrop).toSpec           must_== "3 times d6"
  def rerollSingle= RollSpec(1, 1, 6,  0, Some(Range.inclusive(1,1)), NoDrop).toSpec must_== "d6 reroll 1"
  def rerollRange = RollSpec(1, 4, 6,  0, Some(Range.inclusive(1,2)), NoDrop).toSpec must_== "4d6 reroll 1 to 2"
  def dropLowest  = RollSpec(1, 4, 6,  0, None, DropLowest(1)).toSpec    must_== "4d6 drop lowest"
  def dropLowestN = RollSpec(1, 5, 6,  0, None, DropLowest(2)).toSpec    must_== "5d6 drop lowest 2"
  def dropHighest = RollSpec(1, 4, 6,  0, None, DropHighest(1)).toSpec   must_== "4d6 drop highest"
  def fullAbility = RollSpec(1, 4, 6,  3, None, DropLowest(1)).toSpec    must_== "4d6+3 drop lowest"

  def roundTrip =
    val specs = Seq(
      RollSpec(1, 4, 6,  0, None, DropLowest(1)),
      RollSpec(1, 4, 6,  3, None, DropLowest(1)),
      RollSpec(2, 3, 8, -2, Some(Range.inclusive(1,2)), NoDrop),
      RollSpec(1, 1, 20, 5, None, NoDrop),
    )
    specs.map { spec =>
      RollSpecParser.parseAll(RollSpecParser.roll, spec.toSpec) must beLike {
        case RollSpecParser.Success(parsed, _) => parsed must_== spec
      }
    }.reduce(_ and _)
}
