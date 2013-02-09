import sbt._
import Keys._
import com.github.retronym.SbtOneJar
import org.scalastyle.sbt.ScalastylePlugin

object GamblingBuild extends Build {

	def standardSettings = Seq(
    	exportJars := true
  	) ++ Defaults.defaultSettings ++ SbtOneJar.oneJarSettings ++ ScalastylePlugin.Settings

    lazy val root = Project(id = "gambling",
                            base = file("."),
                            aggregate = Seq(dataLoader, rater, utils))

    lazy val dataLoader = Project(id = "data-loader",
    							   base = file("data-loader"),
    							   settings = standardSettings) dependsOn(utils)

    lazy val rater = Project(id = "rater",
    							   base = file("rater"),
    							   settings = standardSettings) dependsOn(utils)

    lazy val utils = Project(id = "utils",
    							   base = file("utils"),
    							   settings = standardSettings)
}