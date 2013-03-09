package com.labfabulous

import akka.actor.{OneForOneStrategy, Actor}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import com.labfabulous.FileSystemCleaner.Stop
import com.labfabulous.FileSystemCleaner.Clean
import concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import com.labfabulous.ProgressListener.Progress

object FileSystemCleaner {
  case class Clean()
  case class Stop()
}
class FileSystemCleaner(root: String, shouldDeleteFile: (Path => Boolean)) extends Actor {

  override val supervisorStrategy = {
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = new FiniteDuration(2, TimeUnit.MINUTES)) {
      case _: OutOfMemoryError => Restart
      case _: Exception => Escalate
    }
  }

  private val cleaningVisitor = new Visitor

  def clean() {
      println("Doing clean up...")
      Files.walkFileTree(Paths.get(root), cleaningVisitor)
      println("Done clean up.")
  }

  def receive = {
    case Clean() => clean()
    case Stop() =>
  }

  class Visitor extends SimpleFileVisitor[Path] {
    override def visitFile(path: Path, attrs: BasicFileAttributes) = {
      sender ! Progress()
      if (!Files.isHidden(path) && shouldDeleteFile(path))
        Files.delete(path)
      FileVisitResult.CONTINUE
    }
  }
}
