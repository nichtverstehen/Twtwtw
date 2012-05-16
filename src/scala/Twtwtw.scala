import com.mongodb.casbah.Imports._
import dispatch._
import java.util.Date
import scala.util.parsing.json._
import util.Properties

object Index {
  val twtwtwDb = openDb()
  val indexColl = twtwtwDb("index")
  val tweetsColl = twtwtwDb("tweets")
  val usersColl = twtwtwDb("users")
  indexColl.ensureIndex(MongoDBObject("user" -> 1, "term" -> 1))

  def openDb(): MongoDB = {
    val mongoUrl = Properties.envOrElse("MONGOHQ_URL", "mongodb://127.0.0.1:27017/twtwtw")
    val regex = """mongodb://(?:(\w+):(\w+)@)?([\w|\.]+):(\d+)/(\w+)""".r
    val regex(u, p, host, port, dbName) = mongoUrl
    val mongoConn = MongoConnection(host, port.toInt)
    val twtwtwDb = mongoConn(dbName)
    if (u != null) twtwtwDb.authenticate(u, p)
    twtwtwDb
  }

  def indexTweet(tweet: Search.Tweet) {
    tweetsColl += Search.Tweet.toDb(tweet)

    val terms = Search.termize(tweet.text)
    for (val Search.Term(word, start, end, kind) <- terms) {
      val item = Search.IndexItem(word, tweet.user, kind, tweet.id, start, end)
      indexColl += Search.IndexItem.toDb(item)
    }
  }

  def loadUserFeed(feed_url: String): List[Search.Tweet] = {
    val resp = Http(url(feed_url) as_str)
    val obj = JSON.parseFull(resp).asInstanceOf[Option[List[Map[String, Any]]]]
    val tweets = obj.map( _.flatMap(Search.Tweet.fromService) )
    tweets.getOrElse(List())
  }

  def feedUrl(userParam: String, since: Long, max: Long): String = {
    var url = "http://api.twitter.com/1/statuses/user_timeline.json?trim_user=true&include_entities=true&include_rts=true&count=200"
    url = url + "&" + userParam
    if (max != 0) url = url + "&max_id=" + max
    if (since != 0) url = url + "&since_id=" + since
    url
  }

  def indexUser(user_name: String): Boolean = {
    println("indexing "+user_name)

    val feed_url = feedUrl("screen_name="+user_name, 0, 0)

    val tweets = loadUserFeed(feed_url)
    println("indexing "+user_name+": "+tweets.length+"tweets")
    if (tweets.isEmpty) return false

    val user_id = tweets.head.user
    val max_id = tweets.maxBy(_.id).id

    if (!usersColl.find(MongoDBObject("user_id" -> user_id)).isEmpty) {
      println("indexing "+user_name+": already indexed")
      return true
    }

    val mongoUser = MongoDBObject("user_id" -> user_id, "user_name" -> user_name, "max_id" -> max_id)
    usersColl += mongoUser

    tweets.foreach(indexTweet)
    true
  }

  def searchTerm(user_id: Long, term: String): List[Search.IndexItem] = {
    val res = indexColl.find(MongoDBObject("term" -> term, "user" -> user_id))
    val tweet_ids = res.map((x) => x: MongoDBObject).flatMap(Search.IndexItem.fromDb)
    tweet_ids.toList
  }

  def searchWords(user_id: Long, query: String): List[(Search.Tweet, List[Search.IndexItem])] = {
    println("Searching " + user_id + " for " + query)
    val terms = Search.termize(query)

    val occurencesByTerm = terms.map((term: Search.Term) => searchTerm(user_id, term.s))
    val tweetIds = occurencesByTerm.map(_.map(_.tweet)).map((l) => Set(l:_*))
    val commonTweets = tweetIds.reduce((l,r) => l.intersect(r))
    val occurencesInCommon = occurencesByTerm.map(_.filter((t) => commonTweets.contains(t.tweet)))
    val allOccurences = occurencesInCommon.reduce((l,r) => l.union(r))
    val grouped = allOccurences.groupBy(_.tweet).toList

    val tweets = grouped.flatMap({ case ((tweet_id: scala.Long, occur: scala.List[Search.IndexItem])) =>
      tweetsColl
        .findOne(MongoDBObject("id" -> tweet_id))
        .map((z) => z: MongoDBObject)
        .flatMap(Search.Tweet.fromDb)
        .map((z) => (z, occur))})
    tweets.sortBy(_._1.date)(Ordering[Date].reverse)
  }

  def mapUser(user_name: String): Option[Long] = {
    usersColl.findOne(MongoDBObject("user_name" -> user_name))
      .map((z)=>z:MongoDBObject)
      .flatMap(_.get("user_id"))
      .map(_.asInstanceOf[Long])
  }
}

object Test {
  def main(args: Array[String]) {
    val screen_name = "dhh"
    val uid = Index.mapUser(screen_name)
    //Index.indexUser(screen_name)
    println(Index.searchWords(uid.get, "few weeks places"))
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

  }
}
