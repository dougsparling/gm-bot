package ca.dougsparling.gmbot.roller

import scala.collection.mutable

trait Gaussian {
  def nextGaussian(): Double
}

trait FixedGaussian {
  val fixedGaussians: mutable.Stack[Double]
  def gaussian = new Gaussian {
    override def nextGaussian() = fixedGaussians.pop()
  }
}

trait SecureGaussian {
  lazy val gaussianRng = new java.security.SecureRandom()
  def gaussian = new Gaussian {
    override def nextGaussian() = gaussianRng.nextGaussian()
  }
}
