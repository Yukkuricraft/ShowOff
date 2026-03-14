ThisBuild / scalaVersion := "3.7.2"

lazy val root = (project in file("."))
  .settings(
    name := "Show Off",
    organization := "net.lunapixu",
    version := "0.1.0-SNAPSHOT"
  )

libraryDependencies += "io.papermc.paper" % "paper-api" % "1.21.4-R0.1-SNAPSHOT" % Provided

resolvers += Resolver.sonatypeCentralSnapshots
resolvers += "papermc" at "https://repo.papermc.io/repository/maven-public"