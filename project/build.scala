import sbt._

object MyApp extends Build
{
  lazy val root =
    Project("root", file(".")) dependsOn(unfilteredScalate)
  lazy val unfilteredScalate =
    file("unfiltered-scalate")
}