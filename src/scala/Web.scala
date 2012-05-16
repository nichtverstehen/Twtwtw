import unfiltered.request._
import unfiltered.response._
import unfiltered.jetty._
import unfiltered.scalate.Scalate
import org.fusesource.scalate.TemplateEngine
import util.Properties

object Handlers {
  implicit val engine = new TemplateEngine(List(new java.io.File("templates")))

  def wrapSubstring(s: String, start: Int, end: Int, left: String, right: String): String = {
    s.substring(0, start) + left + s.substring(start, end) + right + s.substring(end, s.length)
  }

  def enrichTweet(user_name: String)(tw: Search.Tweet, occur: List[Search.IndexItem]): Map[String, Any] = {
    var props = Utils.getCCParams(tw)

    val rev_occurs = occur.sortBy(_.start)(Ordering.Int.reverse)
    val rich_text = rev_occurs.foldLeft(tw.text)((s, item) => wrapSubstring(s, item.start, item.end, "<strong>", "</strong>"))

    props += ("tweet_id_str" -> tw.id.toString)
    props += ("user_name" -> user_name)
    props += ("rich_text" -> rich_text)
    props
  }

  val intent = unfiltered.Cycle.Intent[Any, Any] {
    case req@GET(Path("/")) => Ok ~> Scalate(req, "main.mustache")
    case req@GET(Path("/search")) => {
      val Params(params) = req
      val user: String = params("user").head
      val words: String = params("words").head

      val userId = { Index.mapUser(user).orElse( { if (Index.indexUser(user)) Index.mapUser(user) else None } ) }
      if (userId.isEmpty) {
          Ok ~> Scalate(req, "main.mustache", "error" -> true)
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
