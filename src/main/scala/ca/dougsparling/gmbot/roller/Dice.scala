package ca.dougsparling.gmbot.roller

import scala.collection.mutable

trait Dice {
  def roll(faces: Int): Int
}

trait FixedDice {
  val fixed: mutable.Stack[Int]
  def dice = new Dice {
    override def roll(faces: Int): Int = fixed.pop()
  }
}

trait SecureDice {
  lazy val roller = java.security.SecureRandom.getInstanceStrong
  def dice = new Dice {
    override def roll(faces: Int) = roller.nextInt(faces) + 1
  }
}