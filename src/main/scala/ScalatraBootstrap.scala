import ca.dougsparling.gmbot._
import org.scalatra._
import jakarta.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) = {
    context.mount(new DiscordServlet, "/discord/*")
    context.mount(new SlackServlet,   "/*")
  }
}
