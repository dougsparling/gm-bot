package ca.dougsparling.gmbot

import org.scalatra.test.specs2._

class GmBotServletSpec extends ScalatraSpec { def is = s2"""
  POST /roll on GmBotServlet
    should return status 200                                $postRoll200
    should have content-type application/json               $postRollJson
    should have a Slack response                            $postRollSuccess
    should report parse errors                              $postRollError
    should approximate when too many dice are rolled        $postRollApproxManyDice
    should approximate when too many faces are rerolled     $postRollApproxManyRerolls
  """

  addServlet(classOf[SlackServlet], "/*")

  def postRoll200 = postRoll("5d6") {
    status must_== 200
  }

  def postRollJson = postRoll("5d6") {
    header("Content-Type") must contain("application/json")
  }

  def postRollSuccess = postRoll("5d6") {
    body must =~("Rolled for doug: \\d{1,2}")
  }

  def postRollError = postRoll("5d0") {
    body must contain("Help")
  }

  def postRollApproxManyDice = postRoll("2 times 9999d6") {
    body must contain("statistical approximation")
  }

  def postRollApproxManyRerolls = postRoll("2000d6 reroll 1 to 5") {
    body must contain("statistical approximation")
  }

  def postRoll[A](text: String)(f: => A) = {
    post("/roll", Map("text" -> text, "command" -> "/roll", "user_name" -> "doug"))(f)
  }
}
