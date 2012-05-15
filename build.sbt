name := "twtwtw"

version := "1.0"

scalaVersion := "2.9.1"

scalaSource in Compile <<= baseDirectory(_ / "src" / "scala")

javaSource in Compile <<= baseDirectory(_ / "src" / "java")

scalaSource in Test <<= baseDirectory(_ / "test")

libraryDependencies ++= Seq(
  "net.databinder" %% "dispatch-http" % "0.8.8"
)

libraryDependencies += "com.mongodb.casbah" %% "casbah" % "2.1.5-1"

libraryDependencies += "net.databinder" %% "unfiltered-filter" % "0.6.2"