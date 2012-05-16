import unfiltered.request._
import unfiltered.response._
import unfiltered.jetty._
import unfiltered.scalate.Scalate
import org.fusesource.scalate.TemplateEngine
import util.Properties

object Handlers {
  implicit val engine = new TemplateEngine(List(new java.io.File("templates")))

  def enrichTweet(user_name: String)(tw: Search.Tweet, occur: Search.IndexItem): Map[String, Any] = {
    var props = Utils.getCCParams(tw)
    props += ("tweet_id_str" -> tw.id.toString)
    props += ("user_name" -> user_name)
    props
  }

  val intent = unfiltered.Cycle.Intent[Any, Any] {
    case req@GET(Path("/")) => Ok ~> Scalate(req, "main.mustache")
    case req@GET(Path("/search")) => {
      val Params(params) = req
      val user: String = params("user").head
      val words: String = params("words").head

      val userId = Index.mapUser(user)
      if (userId.isEmpty) {
        Ok ~> Scalate(req, "main.mustache", "results" -> false, "user" -> user, "words" -> words)
      }
      else {
        val results = Index.searchWords(userId.get, words)
        val rich_results = results.map((tw) => enrichTweet(user)(tw._1, tw._2))
        Ok ~> Scalate(req, "main.mustache", "results" -> true, "user" -> user, "words" -> words, "tweets" -> rich_results)
      }
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
