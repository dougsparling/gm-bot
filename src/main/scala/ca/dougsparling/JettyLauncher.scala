package ca.dougsparling

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.ee10.servlet.DefaultServlet
import org.eclipse.jetty.ee10.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher {
  def main(args: Array[String]): Unit = {
    val port = if(System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

    val server = new Server(port)
    val context = new WebAppContext()
    context.setContextPath("/")
    val codeSource = getClass.getProtectionDomain.getCodeSource.getLocation.toExternalForm
    val war = if (codeSource.endsWith(".jar")) codeSource else "src/main/webapp"
    context.setWar(war)
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    server.setHandler(context)

    server.start()
    server.join()
  }
}
