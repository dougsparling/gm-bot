package ca.dougsparling.gmbot

import org.scalatra.test.specs2._

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class GmBotServletSpec extends ScalatraSpec { def is =
  "POST /roll on GmBotServlet"                     ^
    "should return status 200"                     ! postRoll200^
    "should have content-type application/json"    ! postRollJson^
    "should have a Slack response"                 ! postRollSuccess^
    "should report parse errors"                   ! postRollError^
                                                   end

  addServlet(classOf[GmBotServlet], "/*")

  def postRoll200 = postRoll("5d6") {
    status must_== 200
  }

  def postRollJson = postRoll("5d6") {
    header must havePair("Content-Type" -> "application/json; charset=UTF-8")
  }

  def postRollSuccess = postRoll("5d6") {
    body must beEqualTo("{}")
  }

  def postRollError = postRoll("5d0") {
    body must contain("Help")
  }

  def postRoll[A](text: String)(f: => A) = {
    post("/roll", Map("text" -> text, "command" -> "/roll"))(f)
  }
}
