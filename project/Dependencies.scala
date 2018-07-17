import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "0.10"
    val fs2        = "0.10.5"
    val circe      = "0.9.3"
    val amqpClient = "4.6.0"
    val logback    = "1.1.3"
    val monix      = "3.0.0-RC1"

    val scalaTest  = "3.0.1"
    val scalaCheck = "1.13.4"
  }

  object Libraries {
    lazy val amqpClient = "com.rabbitmq"  % "amqp-client"  % Versions.amqpClient
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2Core    = "co.fs2"        %% "fs2-core"    % Versions.fs2

    // Examples
    lazy val monix   = "io.monix"       %% "monix"          % Versions.monix
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    // Json libraries
    def circe(artifact: String): ModuleID = "io.circe" %% artifact % Versions.circe

    lazy val circeCore    = circe("circe-core")
    lazy val circeGeneric = circe("circe-generic")
    lazy val circeParser  = circe("circe-parser")

    // Scala test libraries
    lazy val scalaTest  = "org.scalatest"  %% "scalatest"  % Versions.scalaTest  % "test"
    lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck % "test"
  }

}
