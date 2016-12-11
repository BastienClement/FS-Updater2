name := "fs-updater"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= Seq(
	"org.scalafx" %% "scalafx" % "8.0.102-R11",
	"org.scalafx" %% "scalafxml-core-sfx8" % "0.3",
	"com.mashape.unirest" % "unirest-java" % "1.4.9"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

enablePlugins(JDKPackagerPlugin)

jdkPackagerBasename := "fs-updater"
jdkPackagerType := "all"
