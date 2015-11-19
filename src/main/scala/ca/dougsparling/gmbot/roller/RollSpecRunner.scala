package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.{DropLowest, DropHighest, NoDrop, RollSpec}

/**
  * Takes a RollSpec and produces a Result describing an actual sequence of die rolls
  * as described by the spec. Uses `dice` for generating the die roll results.
  */
trait RollSpecRunner {
  protected def dice: Dice

  def run(spec: RollSpec): Either[Result, String] = {
    validate(spec) match {
      case None => Left(rollAll(spec))
      case Some(error) => Right(error)
    }
  }

  private def rollAll(spec: RollSpec) = {
    val batches = (1 to spec.repeat).map { _ => rollBatch(spec) }
    Result(batches)
  }

  private def rollBatch(spec: RollSpec, inProgress: List[Roll] = List(), kept: Int = 0): Batch = {
    if(kept >= spec.number) {
      drop(spec, inProgress.reverse)
    } else {
      val aRoll = dice.roll(spec.die)
      if(spec.shouldReroll(aRoll)) {
        rollBatch(spec, Roll(aRoll, Rerolled) :: inProgress, kept)
      } else {
        rollBatch(spec, Roll(aRoll, Kept) :: inProgress, kept + 1)
      }
    }
  }

  def drop(spec: RollSpec, finalRolls: List[Roll]) = {
    val indicesToDrop = findDropIndices(spec, finalRolls)
    if(indicesToDrop.isEmpty) {
      Batch(finalRolls)
    } else {
      Batch(finalRolls.zipWithIndex.map {
        case (Roll(n, Kept), index) if indicesToDrop.contains(index) => Roll(n, Dropped)
        case (r, _) => r
      })
    }
  }

  def findDropIndices(spec: RollSpec, rolls: List[Roll]): Seq[Int] = {
    // TOOD: there must be an easier way to do this...
    spec.drop match {
      case NoDrop => Seq()
      case DropHighest(n) => rolls.zipWithIndex.filter(_._1.fate == Kept).sortBy(_._1.n).reverse.take(n).map(_._2)
      case DropLowest(n) => rolls.zipWithIndex.filter(_._1.fate == Kept).sortBy(_._1.n).take(n).map(_._2)
    }
  }

  private def validate(spec: RollSpec): Option[String] = spec match {
    case RollSpec(_, rolls, _, _, _, DropLowest(drops)) if rolls <= drops => Some("Can't drop all lowest dice")
    case RollSpec(_, rolls, _, _, _, DropHighest(drops)) if rolls <= drops => Some("Can't drop all highest dice")
    case RollSpec(_, _, die, _, Some(range), _) if range.start == 1 && range.end >= die => Some("All dice would be rerolled")
    case _ => None
  }
}