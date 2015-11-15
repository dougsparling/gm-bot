package ca.dougsparling.gmbot.parser

import scala.util.parsing.combinator.RegexParsers

/**
  * Created by Doug on 14/11/2015.
  */
object RollSpecParser extends RegexParsers {

  override type Elem = Char

  def roll: Parser[RollSpec] = repeat.? ~ times.? ~ die ~ mod.? ~ reroll.? ~ drop.? ^^
    { case r ~ t ~ d ~ m ~ o ~ p => RollSpec(r.getOrElse(1), t.getOrElse(1), d, m.getOrElse(0), o, p.getOrElse(NoDrop)) }

  private def repeat = natural(2) <~ ("x"|"times")
  private def times = natural(4)
  private def die = "d" ~> natural(9)
  private def mod = ("+"|"-") ~ whole(9) ^^ {
      case "+" ~ num => num
      case "-" ~ num => -num
    }

  private def reroll = "reroll" ~> (rangeReroll | singleReroll)

  private def singleReroll = natural(9) ^^ { n => Range.inclusive(n, n, 1) }
  private def rangeReroll = natural(9) ~ ("to" ~> natural(9)) ^^ {
    case x ~ y if x < y => Range.inclusive(x, y)
    case x ~ y if x >= y => Range.inclusive(y, x)
  }

  private def drop = "drop" ~> ("highest"|"lowest") ~ natural(9).? ^^ {
    case "highest" ~ n => DropHighest(n.getOrElse(1))
    case "lowest" ~ n => DropLowest(n.getOrElse(1))
  }

  private def natural(digits: Int) = s"[1-9]\\d{0,${digits-1}}".r ^^ { _.toInt }
  private def whole(digits: Int) = "0".r ^^ { x => 0 } | natural(digits)
}

