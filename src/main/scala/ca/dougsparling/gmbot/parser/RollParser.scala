package ca.dougsparling.gmbot.parser

import scala.util.parsing.combinator.RegexParsers

/**
  * Created by Doug on 14/11/2015.
  */
object RollParser extends RegexParsers {

  override type Elem = Char

  def roll: Parser[Roll] = repeat.? ~ times.? ~ die ~ mod.? ~ reroll.? ~ drop.? ^^
    { case r ~ t ~ d ~ m ~ o ~ p => Roll(r.getOrElse(1), t.getOrElse(1), d, m.getOrElse(0), o, p.getOrElse(NoFilter)) }

  private def repeat = natural(2) <~ whiteSpace.? <~ ("x"|"times") <~ whiteSpace.?
  private def times = natural(4)
  private def die = "d" ~> natural(9)
  private def mod = whiteSpace.? ~> (("+"|"-") ~ (whiteSpace.? ~> whole(9))) ^^ {
      case "+" ~ num => num
      case "-" ~ num => -num
    }

  private def reroll = whiteSpace.? ~> "reroll" ~> whiteSpace.? ~> (rangeReroll | singleReroll)

  private def singleReroll = natural(9) ^^ { n => Range(n, n, 1) }
  private def rangeReroll = natural(9) ~ (whiteSpace.? ~> "to" ~> whiteSpace.? ~> natural(9)) ^^ {
    case x ~ y if x < y => Range(x, y)
    case x ~ y if x >= y => Range(y, x)
  }

  private def drop = whiteSpace.? ~> "drop" ~> whiteSpace.? ~> ("highest"|"lowest") ~ (whiteSpace.? ~> natural(9)).? ^^ {
    case "highest" ~ n => Highest(n.getOrElse(1))
    case "lowest" ~ n => Lowest(n.getOrElse(1))
  }

  private def natural(digits: Int) = s"[1-9]\\d{0,${digits-1}}".r ^^ { _.toInt }
  private def whole(digits: Int) = "0".r ^^ { x => 0 } | natural(digits)
}

