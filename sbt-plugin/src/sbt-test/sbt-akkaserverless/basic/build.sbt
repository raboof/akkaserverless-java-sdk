scalaVersion := "2.13.6"

enablePlugins(AkkaserverlessPlugin)

configs(IntegrationTest)
//Defaults.itSettings

libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.7" % Test)
