import sbt._

object MyApp extends Build
{
  lazy val root =
    Project("root", file(".")) dependsOn(unfilteredScalate)
  lazy val unfilteredScalate =
    uri("git://github.com/unfiltered/unfiltered-scalate#5f1a99c2bca9cb78bb6843003c57b4081de8dc91")
}