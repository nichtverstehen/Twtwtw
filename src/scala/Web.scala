import unfiltered.request._
import unfiltered.response._
import unfiltered.jetty._
import unfiltered.scalate.Scalate
import org.fusesource.scalate.TemplateEngine
import util.Properties

object Handlers {
  implicit val engine = new TemplateEngine(List(new java.io.File("templates")))

  val intent = unfiltered.Cycle.Intent[Any, Any] {
    case req@GET(Path("/")) => Ok ~> Scalate(req, "main.mustache")
    case req@GET(Path("/search")) => {
      val Params(params) = req
      val user: String = params("user").head
      val words: String = params("words").head

      val results = Index.searchWord(user, words)

      Ok ~> Scalate(req, "main.mustache", "results" -> true, "user" -> user, "words" -> words, "tweets" -> results)
    }

  }
}

object Web {
  def main(args: Array[String]) {
    val port = Properties.envOrElse("PORT", "8080").toInt
    println("Starting on port:" + port)
    Http(port).filter(unfiltered.filter.Planify(Handlers.intent)).run
  }

}
