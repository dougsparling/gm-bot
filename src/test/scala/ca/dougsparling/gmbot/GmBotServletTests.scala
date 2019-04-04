package ca.dougsparling.gmbot

import org.scalatra.test.scalatest._

class GmBotServletTests extends ScalatraFunSuite {

  addServlet(classOf[GmBotServlet], "/*")

  test("GET / on GmBotServlet should return status 200") {
    get("/") {
      status should equal (200)
    }
  }

}
