import sbt._

object Dependencies {

  sealed trait Version {
    val catsEffect = "1.3.1"
    val fs2        = "1.0.5"
    val circe      = "0.11.1"
    val amqpClient = "5.7.1"
    val logback    = "1.2.3"

    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.0"

    val scalaTest  = "3.0.8"
    val scalaCheck = "1.14.0"
  }

  object Scala211Versions extends Version {
    val monix      = "3.0.0-RC3"
    val zio        = "1.0.0-RC8-4"
  }

  object Scala212Versions extends Version {
    val monix      = "3.0.0-RC3"
    val zio        = "1.0.0-RC8-4"
  }

  object Scala213Versions extends Version {
    override val catsEffect = "2.0.0-M4"
    override val fs2        = "1.1.0-M1"
    override val circe      = "0.12.0-M3"
  }

  object Versions {
    val catsEffect = "2.0.0-M4"
    val fs2        = "1.1.0-M1"
    val circe      = "0.12.0-M3"
    val amqpClient = "5.7.1"
    val logback    = "1.2.3"
    val monix      = "3.0.0-RC2"
    val zio        = "1.0-RC5"

    val kindProjector    = "0.10.3"
    val betterMonadicFor = "0.3.0"

    val scalaTest  = "3.0.8"
    val scalaCheck = "1.14.0"
  }

  sealed abstract class Library[V <: Version](val version: V) {
    def circe(artifact: String): ModuleID = "io.circe" %% artifact % version.circe

    lazy val amqpClient = "com.rabbitmq"  % "amqp-client"  % version.amqpClient
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % version.catsEffect
    lazy val fs2Core    = "co.fs2"        %% "fs2-core"    % version.fs2

    // Compiler
    lazy val kindProjector    = "org.typelevel" % "kind-projector"      % version.kindProjector cross CrossVersion.binary
    lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % version.betterMonadicFor

    // Examples
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    // Json libraries
    lazy val circeCore    = circe("circe-core")
    lazy val circeGeneric = circe("circe-generic")
    lazy val circeParser  = circe("circe-parser")

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % version.scalaTest
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % version.scalaCheck
  }

  case object Scala211Libraries extends Library(Scala211Versions) {
    def zio(artifact: String): ModuleID = "dev.zio" %% artifact % version.zio

    // Example Libraries
    lazy val monix = "io.monix" %% "monix" % version.monix
    lazy val zioCore = zio("zio")
    lazy val zioCats = zio("zio-interop-cats")
  }

  case object Scala212Libraries extends Library(Scala212Versions) {
    def zio(artifact: String): ModuleID = "dev.zio" %% artifact % version.zio

    // Example Libraries
    lazy val monix = "io.monix" %% "monix" % version.monix
    lazy val zioCore = zio("zio")
    lazy val zioCats = zio("zio-interop-cats")
  }

  case object Scala213Libraries extends Library(Scala213Versions)

}
