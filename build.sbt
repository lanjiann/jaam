name := "analyzer"

organization := "org.ucombinator"

version := "0-SNAPSHOT"

resolvers += "Ucombinator maven repository on github" at "https://ucombinator.github.io/maven-repo"

libraryDependencies ++= Seq(
        "org.ucombinator.soot" % "soot-all-in-one" % "nightly.20150205",
        "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
        "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
)

mainClass in Compile := Some("org.ucombinator.analyzer.Main")
