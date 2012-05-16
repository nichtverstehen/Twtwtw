import unfiltered.request._
import unfiltered.response._
import unfiltered.jetty._
import unfiltered.scalate.Scalate
import org.fusesource.scalate.TemplateEngine

object Handlers {
  val intent = unfiltered.Cycle.Intent[Any, Any] {
    case _ => ResponseString("Hello")
  }
}

object Web {
  def main(args: Array[String]) {
    val engine = new TemplateEngine(List(new java.io.File("templates")))

    Http(8080).filter(unfiltered.filter.Planify(Handlers.intent)).run
  }

}
