package org.ucombinator.jaam.tools.app

import java.io.FileOutputStream
import java.nio.file._

import scala.collection.JavaConverters._

import org.ucombinator.jaam.{serializer, tools}
import org.ucombinator.jaam.util._

sealed trait Origin

object Origin { // TODO: Move into App object?
  case object APP extends Origin
  case object LIB extends Origin
  case object JVM extends Origin
}

case class PathElement(path: String, root: String, origin: Origin, data: Array[Byte]) {
  private def classData(data: Array[Byte]): List[Array[Byte]] =
    if (path.endsWith(".class")) List(data)
    else if (path.endsWith(".jar")) {
      val entries = org.ucombinator.jaam.util.Jar.entries(new java.io.ByteArrayInputStream(data))
      val classes = for ((e, d) <- entries if e.getName.endsWith(".class")) yield d
      val recursiveClasses = for ((e, d) <- entries if e.getName.endsWith(".jar")) yield classData(d)
      classes ++ recursiveClasses.flatten
    } else Nil
  def classData(): List[Array[Byte]] = classData(data)
}

case class App() extends serializer.Packet {
  var name: Option[String] = None
  var classpath: Array[PathElement] = Array.empty
  object main {
    var className: Option[String] = None
    var methodName: Option[String] = None
  }
  var appPackages: Array[String] = Array.empty
//  object java {
//    var opts = null: String
//  }
}


//jaam-tools app
// TODO: automatically find main and StacMain
// TODO: automatically determine app vs lib classes by finding main
// TODO: automatically find all jar files in subdirectory
object Main {
  var mains: List[String] = List.empty // TODO: set class and method name from mains (error if multiple)

  // relative to root
  def read(root: Path, path: Path, origin: Option[Origin], mainClass: Option[String]): List[PathElement] = {
    if (path.toFile.isDirectory) {
      return Files.newDirectoryStream(path).asScala.toList.flatMap { p =>
        try read(root, p, origin, mainClass)
        catch {
          case e: Exception =>
            println("Ignoring " + root + " and " + p + " because " + e)
            Nil
        }
      }
    } else if (path.toString.endsWith(".class")) {
      val data = Files.readAllBytes(path)
      println(f"${data(0)}%x ${data(1)}%x ${data(2)}%x ${data(3)}%x")
      //if (!data.startsWith(List(0xCA, 0xFE, 0xBA, 0xBE))) {
      //  throw new Exception(f"TTT Malformed class file $path at $root")
      //}
      return List(PathElement(path.toString, root.toString, origin.getOrElse(Origin.APP), data))
    } else if (path.toString.endsWith(".jar")) {
      val data = Files.readAllBytes(path)
      if (!data.startsWith(List(0x50, 0x4B, 0x03, 0x04))) {
        throw new Exception(f"Malformed class file $path at $root")
      }

      val jar = Jar.jar(data)

      def getMains: List[String] = {
        val main = jar.getManifest.getMainAttributes.getValue("Main-Class")

        // TODO: inspect class data for a main method
        if (main != null) List(main)
        else {
          val endingString = mainClass match {
            case Some(s) => f"${s.replace('.', '/')}.class"
            case None => "/StacMain.class"
          }
          Jar.entries(jar).map(_._1.getName)
            .filter(_.endsWith(endingString))
            .map(_.stripSuffix(".class").replace('/', '.'))
        }
      }

      val detectedOrigin = origin match {
        case Some(r) =>
          if (r == Origin.APP) { mains ++= getMains}
          r
        case None =>
          getMains match {
            case Nil => Origin.LIB
            case es => mains ++= es; Origin.APP
          }
      }

      // TODO: set class main (do error if not found, maybe do more searching)

      return List(PathElement(path.toString, root.toString, detectedOrigin, data))
    } else {
      throw new Exception("not a directory, class, or jar")
    }
  }

  def main(input: List[String],
           app: List[String],
           lib: List[String],
           jvm: List[String],
           appPackages: List[String],
           defaultJvm: Boolean,
           detectMain: Boolean,
           mainClass: Option[String],
           mainMethod: Option[String],
           jaam: String) {
    def readList(list: List[String], origin: Option[Origin]) =
      list.flatMap(x => read(Paths.get(x), Paths.get(x), origin, mainClass)).toArray

    val appConfig = App()
    appConfig.classpath ++= readList(input, None)
    appConfig.classpath ++= readList(app, Some(Origin.APP))
    appConfig.classpath ++= readList(lib, Some(Origin.LIB))
    appConfig.classpath ++= readList(jvm, Some(Origin.JVM))

    appConfig.appPackages = appPackages.toArray

    if (defaultJvm) {
      val JVM_JARS = "java-1.8.0-openjdk-headless-1.8.0.65-2.b17.el7_1.x86_64.zip"
      val res = getClass.getResourceAsStream(JVM_JARS)
      for (entry <- Zip.entries(Zip.zip(res))) {
        if (entry._1.getName.endsWith(".jar")) {
          appConfig.classpath :+= PathElement("resource:"+entry._1.getName, JVM_JARS, Origin.JVM, entry._2)
        }
      }
    }

    for (c <- appConfig.classpath) {
      println(f"In ${c.root} found a ${c.origin} file: ${c.path}")
    }

    appConfig.main.className = mainClass match {
      case Some(s) => Some(s)
      case None =>
        if (!detectMain) {
          None
        } else {
          mains match {
            case List() =>
              println("WARNING: No main class found")
              None
            case List(x) => Some(x)
            case xs =>
              println("WARNING: multiple main classes found")
              for (x <- xs) {
                println(f" - $x\n")
              }
              None
          }
        }
    }

    appConfig.main.methodName = mainMethod orElse {
      appConfig.main.className match {
        case Some(_) => Some("main")
        case None => None
      }
    }

    println(f"Main class: ${appConfig.main.className}")
    println(f"Main method: ${appConfig.main.methodName}")

    val outStream = new FileOutputStream(jaam)
    val po = new serializer.PacketOutput(outStream)
    po.write(appConfig)
    po.close()
  }
}

// jaam-tools app --input airplan_1/ --output airplan_1.app.jaam
