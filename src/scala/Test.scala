import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoURI
import dispatch._
import org.tartarus.snowball.ext.englishStemmer
import scala.util.parsing.json._
import util.Properties

object Utils {
  def spansOf[T](f: (T) => Boolean)(s: Seq[T]): List[Seq[T]] = s match {
    case Seq() => Nil
    case x if !f(x.head) =>
      val (_, next) = x span (i => !f(i))
      spansOf(f)(next)
    case x =>
      val (same, rest) = x span f
      val (_, next) = rest span (i => !f(i))
      same :: spansOf(f)(next)
  }
}

object Search {

  object TermKind extends Enumeration { val Word, User, Url = Value }
  type TermKind = TermKind.Value

  case class Token(s: String, start: Int, end: Int)
  case class Term(s: String, start: Int, end: Int, literal: Boolean, tok: Token, kind: TermKind)

  val stemmer = new englishStemmer;

  def tokenize(s: String): Seq[Token] = {
    val f: ((Char, Int)) => Boolean = { case (a: Char, i: Int) => a.isLetterOrDigit }
    val spans = Utils.spansOf(f)(s.zipWithIndex)

    def makeToken(q: Seq[(Char, Int)]) = {
      val s: String = q.map(_._1).mkString
      Token(s, q.head._2, q.last._2+1)
    }

    spans.map(makeToken)
  }

  def stemize(s: String): String = {
    stemmer.setCurrent(s)
    stemmer.stem()
    stemmer.getCurrent()
  }

  def termize(tokens: Seq[Token]): Seq[Term] = {
    tokens.map({ case t@Token(s, b, e) => {
      val stem = stemize(s.toLowerCase)
      Term(stem, b, e, s == stem, t, TermKind.Word)
    } })
  }

}

object Index {
  val mongoUrl = Properties.envOrElse("MONGOHQ_URL", "mongodb://127.0.0.1/twtwtw")
  println(mongoUrl)
  val mongoConn = MongoConnection(MongoURI(mongoUrl))
  val twtwtwDB = mongoConn("twtwtw")
  val indexColl = twtwtwDB("index")
  val tweetsColl = twtwtwDB("tweets")
  val usersColl = twtwtwDB("users")
  indexColl.ensureIndex(MongoDBObject("user" -> 1, "term" -> 1))

  def indexTweet(tweet: Map[String, Any]) {
    val tweet_id = tweet("id").asInstanceOf[Double].round
    val user_id = tweet("user").asInstanceOf[Map[String, Any]]("id").asInstanceOf[Double].round
    val text = tweet("text").asInstanceOf[String]
    val mongoTweet = MongoDBObject("tweet" -> tweet_id, "user_id" -> user_id, "text" -> text)
    tweetsColl += mongoTweet

    val tokens = Search.tokenize(text)
    val terms = Search.termize(tokens)
    for (val Search.Term(word, start, end, literal, _, kind) <- terms) {
      val mongoTerm = MongoDBObject("term" -> word, "user" -> user_id, "tweet" -> tweet_id, "start" -> start,
        "end" -> end, "literal" -> literal, "kind" -> kind.id)
      indexColl += mongoTerm
    }
  }

  def indexUser(userName: String) {
    val feed_url = "http://api.twitter.com/1/statuses/user_timeline.json?screen_name=%s".format(userName)
    val resp = Http(url(feed_url) as_str)
    val obj = JSON.parseFull(resp).asInstanceOf[Option[List[Any]]]
    if (obj.isEmpty) { return }
    val list = obj.get.asInstanceOf[List[Map[String, Any]]]
    if (list.isEmpty) { return }

    val userId = list.head("user").asInstanceOf[Map[String, Any]]("id").asInstanceOf[Double].round

    val mongoUser = MongoDBObject("user_id" -> userId, "user_name" -> userName)
    usersColl += mongoUser

    list.foreach(indexTweet)
  }

  def searchWord(userName: String, word: String): List[String] = {
    val term = Search.stemize(word)
    val mongoUser: Option[MongoDBObject] = usersColl.findOne().map((a) => a)
    val user_id_opt = mongoUser.map(_.as[Long]("user_id"))
    if (user_id_opt.isEmpty) return List()
    val user_id = user_id_opt.get
    val res = indexColl.find(MongoDBObject("term" -> term, "user" -> user_id))
    val tweet_ids = res.map(_.as[Long]("tweet"))
    val tweets: List[DBObject] = tweet_ids.flatMap((id) => tweetsColl.findOne(MongoDBObject("tweet" -> id))).toList
    val texts: List[String] = tweets.map(_.as[String]("text"))
    texts
  }
}

object Test {
  def main(args: Array[String]) {
    val screenName = "dhh"
    /*val feed_url = "http://api.twitter.com/1/statuses/user_timeline.json?screen_name=%s".format(screenName)
    val resp = Http(url(feed_url) as_str)
    val obj = JSON.parseFull(resp).asInstanceOf[Option[List[Any]]]

    def proc(props: Map[String, Any]): Seq[Search.Token] = {
      val text: String = props.getOrElse("text", "").asInstanceOf[String]
      Search.tokenize(text)
    }

    val f: Any => Map[String, Any] = _.asInstanceOf[Map[String, Any]]
    val tokens: Option[List[Seq[Search.Token]]] = obj.map( (tweets: List[Any]) => tweets.map(f andThen proc) )
    val terms = tokens.map(_.map(Search.termize))

    println(terms)*/

    Index.indexUser("dhh")

    println(Index.searchWord("dhh", "place"))
  }
}
