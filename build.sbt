import java.nio.file.Paths

import com.virtuslab.iat
import com.virtuslab.iat.dsl.Label.Name
import com.virtuslab.iat.dsl.TCP
import com.virtuslab.iat.kubernetes.dsl.{Application, Configuration, Container, Namespace}
import com.virtuslab.iat.skuber.details.resourceRequirements
import skuber.Resource

name := "test-infra-as-type"

version := "0.1"

scalaVersion := "2.13.3"
lazy val foo = taskKey[Unit]("foo")
foo := {
  val s                    = state.value
  val extracted: Extracted = Project extract s
  val logger               = extracted.get(sLog)
  logger.info("foo was called")
}
lazy val upsertTask = taskKey[Unit]("upsert task")
import java.nio.file.Paths

import com.virtuslab.iat
import com.virtuslab.iat.dsl.Label.Name
import com.virtuslab.iat.dsl.TCP
import com.virtuslab.iat.kubernetes.dsl.{ Application, Configuration, Container, Namespace }
import skuber.Resource

Global / concurrentRestrictions := Seq(
  Tags.limit(Tags.CPU, 2),
  Tags.limit(Tags.Network, 10),
  Tags.limit(Tags.Test, 1),
  Tags.limitAll( 15 )
)

upsertTask := {

  val s                    = state.value
  val extracted: Extracted = Project extract s
  val logger               = extracted.get(sLog)

  import SkuberApp._
  import iat.kubernetes.dsl.ops._
  import iat.skuber.dsl._

  val ns = Namespace(Name("test") :: Nil)
  val conf = Configuration(
    Name("app") :: Nil,
    data = Map(
      "config.yaml" -> """
                         |listen: :8080
                         |logRequests: true
                         |connectors:
                         |- type: file
                         |  uri: file:///opt/test.txt
                         |  pathPrefix: /health
                         |""".stripMargin,
      "test.txt" -> """
                      |I'm testy tester, being tested ;-)
                      |""".stripMargin
    )
  )

  val app = Application(
    Name("app") :: Nil,
    containers = Container(
      Name("app") :: Nil,
      image = "quay.io/virtuslab/cloud-file-server:v0.0.6",
      command = List("cloud-file-server"),
      args = List("--config", "/opt/config.yaml"),
      ports = TCP(8080) :: Nil
    ) :: Nil,
    configurations = conf :: Nil,
    mounts = conf.mount("config", Paths.get("/opt/")) :: Nil
  )

  import iat.skuber.details._
  val appDetails = resourceRequirements(
    filter = _.name == "app",
    Resource.Requirements(
      requests = Map(
        "cpu" -> "100m",
        "memory" -> "10Mi"
      ),
      limits = Map(
        "cpu" -> "200m",
        "memory" -> "200Mi"
      )
    )
  )

  import iat.kubernetes.dsl.experimental._
  import iat.skuber.deployment._
  import iat.skuber.experimental._
  import skuber.json.format._

  try {
    val results =
      ns.interpret.upsert.deinterpret.summary ::
        conf
          .inNamespace(ns)
          .interpret
          .upsert
          .deinterpret
          .summary ::
        app
          .inNamespace(ns)
          .interpret
          .map(appDetails)
          .upsert
          .deinterpret
          .summary :: Nil

    results.foreach(s => logger.info(s.asString))

  } catch {
    case e: Throwable => {
      logger.info(e.getCause.toString)
      logger.info(e.getStackTrace.mkString("\n"))
    }
  }

  logger.info("foo")

//  close()
}