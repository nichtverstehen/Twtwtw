import unfiltered.request._
import unfiltered.response._
import unfiltered.jetty._
import unfiltered.scalate.Scalate
import org.fusesource.scalate.TemplateEngine

object Web {
  def main(args: Array[String]) {
    val engine = new TemplateEngine(List(new java.io.File("templates")))

    Http(8080).context("/public"){ ctx: ContextBuilder =>
      ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
    }.filter(unfiltered.filter.Planify {
      case req => Ok ~> Scalate(req, "hello.ssp")(engine)
    }).run
  }

}
