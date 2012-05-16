import com.mongodb.casbah.Imports._
import com.mongodb.casbah.Implicits
import java.text.{SimpleDateFormat, DateFormat}
import java.util.{Locale, Date}
import org.tartarus.snowball.ext.englishStemmer

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

  def getCCParams(cc: AnyRef) =
    (Map[String, Any]() /: cc.getClass.getDeclaredFields) {(a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(cc))
    }
}

object Search {

  object TermKind extends Enumeration {
    val Word, User, Url = Value
  }

  type TermKind = TermKind.Value

  case class Token(s: String, start: Int, end: Int)

  case class Term(s: String, start: Int, end: Int, kind: TermKind)

  object EntityKind extends Enumeration {
    val User, Url, Hash = Value
  }

  type EntityKind = EntityKind.Value

  case class Entity(id: Long, kind: EntityKind, start: Int, end: Int, text: String, url: String, expanded: String)

  case class Tweet(id: Long, text: String, user: Long, entities: List[Entity], date: Date)

  case class User(id: Long, name: String, display: String)

  case class IndexItem(term: String, user: Long, kind: TermKind, tweet: Long, start: Int, end: Int)

  object IndexItem {
    def fromDb(m: scala.collection.Map[String, AnyRef]): Option[IndexItem] = {
      val item = for (
        val term <- m.get("term").map(_.asInstanceOf[String]);
        val user <- m.get("user").map(_.asInstanceOf[Long]);
        val kind <- m.get("kind").map(_.asInstanceOf[Int]);
        val tweet <- m.get("tweet").map(_.asInstanceOf[Long]);
        val start <- m.get("start").map(_.asInstanceOf[Int]);
        val end <- m.get("end").map(_.asInstanceOf[Int]))
      yield IndexItem(term, user, TermKind(kind), tweet, start, end);
      item.headOption
    }

    def toDb(r: IndexItem): MongoDBObject = {
      MongoDBObject("term" -> r.term, "user" -> r.user, "kind" -> r.kind.id,
        "tweet" -> r.tweet, "start" -> r.start, "end" -> r.end)
    }
  }

  object Tweet {
    val dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH);

    def fromDb(m: scala.collection.Map[String, Any]): Option[Tweet] = {
      val item = for (
          id <- m.get("id").map(_.asInstanceOf[Long]);
          text <- m.get("text").map(_.asInstanceOf[String]);
          user <- m.get("user").map(_.asInstanceOf[Long]);
          entities <- m.get("entities")
            .map((z)=>z.asInstanceOf[BasicDBList]: MongoDBList)
            .map(_.asInstanceOf[Seq[Map[String, Any]]])
            .map(_.flatMap(entityFromDb));
          date <- m.get("date").map(_.asInstanceOf[Date])
        )
        yield Tweet(id, text, user, entities.toList, date)
      item.headOption
    }

    def entityFromDb(m: scala.collection.Map[String, Any]): Option[Entity] = {
      val item = for (
        val id <- m.get("id").map(_.asInstanceOf[Long]);
        val kind <- m.get("kind").map(_.asInstanceOf[Int]);
        val start <- m.get("start").map(_.asInstanceOf[Int]);
        val end <- m.get("end").map(_.asInstanceOf[Int]);
        val text <- m.get("text").map(_.asInstanceOf[String]);
        val url <- m.get("url").map(_.asInstanceOf[String]);
        val expanded <- m.get("expanded").map(_.asInstanceOf[String])
      )
      yield Entity(id, EntityKind(kind), start, end, text, url, expanded)
      item.headOption
    }

    def fromService(m: Map[String, Any]): Option[Tweet] = {
      val item = for (
        val id <- m.get("id_str").map(_.asInstanceOf[String].toLong);
        val text <- m.get("text").map(_.asInstanceOf[String]);
        val user <- m.get("user").flatMap(_.asInstanceOf[Map[String, Any]].get("id_str")).map(_.asInstanceOf[String].toLong);
        val entities <- Some(List());
        val date <- m.get("created_at").map(_.asInstanceOf[String]).map(dateFormat.parse)
      )
      yield Tweet(id, text, user, entities, date)
      item.headOption
    }

    def toDb(r: Tweet): MongoDBObject = {
      MongoDBObject("id" -> r.id, "text" -> r.text, "user" -> r.user,
        "entities" -> r.entities.map(entityToDb), "date" -> r.date)
    }

    def entityToDb(r: Entity): MongoDBObject = {
      MongoDBObject("id" -> r.id, "kind" -> r.kind, "start" -> r.start,
        "end" -> r.end, "text" -> r.text, "url" -> r.url, "expanded" -> r.expanded)
    }
  }

  val stemmer = new englishStemmer;

  def tokenize(s: String): Seq[Token] = {
    val f: ((Char, Int)) => Boolean = {
      case (a: Char, i: Int) => a.isLetterOrDigit
    }
    val spans = Utils.spansOf(f)(s.zipWithIndex)

    def makeToken(q: Seq[(Char, Int)]) = {
      val s: String = q.map(_._1).mkString
      Token(s, q.head._2, q.last._2 + 1)
    }

    spans.map(makeToken)
  }

  def stemize(s: String): String = {
    stemmer.setCurrent(s)
    stemmer.stem()
    stemmer.getCurrent()
  }

  def termize(s: String): Seq[Term] = {
    val tokens = tokenize(s)
    tokens.map({
      case t@Token(s, b, e) => {
        val stem = stemize(s.toLowerCase)
        Term(stem, b, e, TermKind.Word)
      }
    })
  }

}