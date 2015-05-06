import de.johoop.jacoco4sbt.JacocoPlugin._
import org.scalastyle.sbt.ScalastylePlugin
import Common._
import uk.gov.dvla.vehicles.sandbox
import sandbox.ProjectDefinitions.{osAddressLookup, vehicleAndKeeperLookup, vehiclesAcquireFulfil, legacyStubs, gatlingTests}
import uk.gov.dvla.vehicles.sandbox.ProjectDefinitions.emailService
import sandbox.Sandbox
import sandbox.SandboxSettings
import sandbox.Tasks
import io.gatling.sbt.GatlingPlugin
import GatlingPlugin.Gatling
import com.typesafe.sbt.rjs.Import.RjsKeys.webJarCdns

name := "vehicles-acquire-online"

version := versionString

organization := organisationString

organizationName := organisationNameString

scalaVersion := scalaVersionString

scalacOptions := scalaOptionsSeq

publishTo.<<=(publishResolver)

credentials += sbtCredentials

resolvers ++= projectResolvers

pipelineStages := Seq(rjs, digest, gzip)

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

lazy val acceptanceTestsProject = Project("acceptance-tests", file("acceptance-tests"))
  .dependsOn(root % "test->test")
  .disablePlugins(PlayScala, SbtWeb)
  .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings:_*)

lazy val gatlingTestsProject = Project("gatling-tests", file("gatling-tests"))
  .disablePlugins(PlayScala, SbtWeb)
  .enablePlugins(GatlingPlugin)

libraryDependencies ++= Seq(
  cache,
  filters,
  "dvla" %% "vehicles-presentation-common" % "2.21" withSources() withJavadoc() exclude("junit", "junit-dep"),
  "dvla" %% "vehicles-presentation-common" % "2.21" % "test" withSources() classifier "tests" withJavadoc() exclude("junit", "junit-dep"),
  "com.google.guava" % "guava" % "15.0" withSources() withJavadoc(), // See: http://stackoverflow.com/questions/16614794/illegalstateexception-impossible-to-get-artifacts-when-data-has-not-been-loaded
  "org.seleniumhq.selenium" % "selenium-java" % "2.43.0" % "test" withSources() withJavadoc(),
  "com.github.detro" % "phantomjsdriver" % "1.2.0" % "test" withSources() withJavadoc(),
  "org.mockito" % "mockito-all" % "1.9.5" % "test" withSources() withJavadoc(),
  "org.slf4j" % "log4j-over-slf4j" % "1.7.7" % "test" withSources() withJavadoc(),
  "com.github.tomakehurst" % "wiremock" % "1.46" % "test" withSources() withJavadoc() exclude("log4j", "log4j"),
  "org.scalatest" %% "scalatest" % "2.2.1" % "test" withSources() withJavadoc(),
  "com.google.inject" % "guice" % "4.0-beta4" withSources() withJavadoc(),
  "com.tzavellas" % "sse-guice" % "0.7.1" withSources() withJavadoc(), // Scala DSL for Guice
  "commons-codec" % "commons-codec" % "1.9" withSources() withJavadoc(),
  "org.apache.httpcomponents" % "httpclient" % "4.3.4" withSources() withJavadoc(),
  "org.webjars" % "requirejs" % "2.1.14-1",
  "junit" % "junit" % "4.11",
  "junit" % "junit-dep" % "4.11"
)

pipelineStages := Seq(rjs, digest, gzip)

val myTestOptions =
  if (System.getProperty("include") != null ) {
    Seq(testOptions in Test += Tests.Argument("include", System.getProperty("include")))
  } else if (System.getProperty("exclude") != null ) {
    Seq(testOptions in Test += Tests.Argument("exclude", System.getProperty("exclude")))
  } else Seq.empty[Def.Setting[_]]

myTestOptions

// If tests are annotated with @LiveTest then they are excluded when running sbt test
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-l", "helpers.tags.LiveTest")

javaOptions in Test += System.getProperty("waitSeconds")

concurrentRestrictions in Global := Seq(Tags.limit(Tags.CPU, 4), Tags.limit(Tags.Network, 1000), Tags.limit(Tags.Test, 4))

sbt.Keys.fork in Test := false

jacoco.settings

parallelExecution in jacoco.Config := false

parallelExecution in Test in acceptanceTestsProject := true

// Using node to do the javascript optimisation cuts the time down dramatically
JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

// Disable documentation generation to save time for the CI build process
sources in doc in Compile := List()

ScalastylePlugin.Settings

net.virtualvoid.sbt.graph.Plugin.graphSettings

webJarCdns := Map()

// ====================== Sandbox Settings ==========================
lazy val osAddressLookupProject = osAddressLookup("0.13").disablePlugins(PlayScala, SbtWeb)
lazy val vehicleAndKeeperLookupProject = vehicleAndKeeperLookup("0.11.1").disablePlugins(PlayScala, SbtWeb)
lazy val vehiclesAcquireFulfilProject = vehiclesAcquireFulfil("0.8").disablePlugins(PlayScala, SbtWeb)
lazy val emailServiceProject = emailService("0.5").disablePlugins(PlayScala, SbtWeb)
lazy val legacyStubsProject = legacyStubs("1.0-SNAPSHOT").disablePlugins(PlayScala, SbtWeb)

SandboxSettings.portOffset := 19000

SandboxSettings.applicationContext := ""

SandboxSettings.webAppSecrets := "ui/dev/vehicles-acquire-online.conf.enc"

SandboxSettings.osAddressLookupProject := osAddressLookupProject

SandboxSettings.vehicleAndKeeperLookupProject := vehicleAndKeeperLookupProject

SandboxSettings.vehiclesAcquireFulfilProject := vehiclesAcquireFulfilProject

SandboxSettings.emailServiceProject := emailServiceProject

SandboxSettings.legacyStubsProject := legacyStubsProject

SandboxSettings.runAllMicroservices := {
  Tasks.runLegacyStubs.value
  Tasks.runOsAddressLookup.value
  Tasks.runVehicleAndKeeperLookup.value
  Tasks.runVehiclesAcquireFulfil.value
  Tasks.runEmailService.value
}

SandboxSettings.loadTests := (test in Gatling in gatlingTestsProject).value

SandboxSettings.acceptanceTests := (test in Test in acceptanceTestsProject).value

SandboxSettings.bruteForceEnabled := true

Sandbox.sandboxTask

Sandbox.sandboxAsyncTask

Sandbox.gatlingTask

Sandbox.acceptTask

Sandbox.cucumberTask

Sandbox.acceptRemoteTask

resolvers ++= Seq("Bintray-repo" at "http://dl.bintray.com/dvla/maven/")

