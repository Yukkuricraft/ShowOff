ThisBuild / scalaVersion := "3.7.2"

lazy val root = (project in file("."))
  .settings(
    name := "ShowOff",
    organization := "net.lunapixu",
    version := "0.2.0-SNAPSHOT",
    artifactName := { (sv: ScalaVersion, module: ModuleID, artifact: Artifact) =>
      artifact.name + '-' + module.revision + '.' + artifact.extension
    }
  )

libraryDependencies += "io.papermc.paper" % "paper-api" % "1.21.4-R0.1-SNAPSHOT" % Provided

resolvers += Resolver.sonatypeCentralSnapshots
resolvers += "papermc" at "https://repo.papermc.io/repository/maven-public"