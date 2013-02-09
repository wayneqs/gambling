scalaVersion in ThisBuild := "2.10.0"

resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
                  "OSS Sonatype Repository" at "http://oss.sonatype.org/content/repositories/releases")

libraryDependencies in ThisBuild += "com.typesafe" % "config" % "1.0.0"

libraryDependencies in ThisBuild += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"

libraryDependencies in ThisBuild += "org.slf4j" % "slf4j-simple" % "1.7.2"

libraryDependencies in ThisBuild += "com.typesafe.akka" %% "akka-actor" % "2.1.0"

libraryDependencies in ThisBuild += "com.typesafe.akka" %% "akka-testkit" % "2.1.0" % "test"

libraryDependencies in ThisBuild += "org.specs2" %% "specs2" % "1.13" % "test"

libraryDependencies in ThisBuild += "org.mockito" % "mockito-all" % "1.9.5" % "test"

libraryDependencies in ThisBuild += "org.scalaj" % "scalaj-time_2.9.2" % "0.6"
