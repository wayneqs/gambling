package com.labfabulous

import java.nio.file._
import org.bson.types.ObjectId
import java.io.{FileFilter, File}
import concurrent.stm._

class FileCreator {
  private val current = (Ref(""), Ref(0))
  private val MAX_FILES_PER_DIRECTORY = 20000

  def create(baseDirectory: Path, bytes: Array[Byte]) = {
    val id = new ObjectId
    val filePath = Paths.get(useCurrent(baseDirectory), id.toString)
    Files.write(filePath, bytes, StandardOpenOption.CREATE)
    filePath
  }

  private def useCurrent(baseDirectory: Path) = {
    atomic {
      implicit txn =>
        if (current._1().isEmpty || current._2() >= MAX_FILES_PER_DIRECTORY) {
          val (path, count) = findDirectoryWithFewerFilesThan(baseDirectory)
          current._1() = path
          current._2() = count
        }
        current._2 transform(_ + 1)
        current._1()
    }
  }

  private def createNewDir(baseDirectory: Path) = {
    val dirName = new ObjectId
    val newDirPath = Paths.get(baseDirectory.toString, dirName.toString)
    newDirPath.toFile.mkdir()
    newDirPath.toString
  }

  private def findDirectoryWithFewerFilesThan(baseDirectory: Path) = {
    val candidates = new File(baseDirectory.toString).listFiles(new DirWithFileCountCap(MAX_FILES_PER_DIRECTORY))
    if (candidates.size == 0) (createNewDir(baseDirectory), 0) else (candidates(0).toPath.toString, candidates.size)
  }

  private class DirWithFileCountCap(count: Int) extends FileFilter {
    def accept(file: File) = {
      file.isDirectory && !file.isHidden && file.list().size < count
    }
  }
}
