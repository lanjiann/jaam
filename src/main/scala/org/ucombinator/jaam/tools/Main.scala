package org.ucombinator.jaam.tools

import org.rogach.scallop._

object Conf {
  def extractSeqFromOptString(optString: ScallopOption[String], separator: String = ":"): Seq[String] = {
    optString.toOption.getOrElse("").split(separator).filter(_.nonEmpty)
  }
}

class Conf(args : Seq[String]) extends ScallopConf(args = args) {
  banner("Usage: jaam-tools [subcommand] [options]")
  addSubcommand(new Print)
  addSubcommand(new Validate)
  addSubcommand(new Info)
  addSubcommand(new Cat)
  addSubcommand(new Coverage)
  addSubcommand(new Coverage2)
  addSubcommand(new MissingReturns)
  addSubcommand(new LoopDepthCounter)
  addSubcommand(new LoopAnalyzer)
  addSubcommand(new ListItems)
  addSubcommand(new FindMain)
  addSubcommand(new Taint)
  verify()
}

abstract class Main(name: String) extends Subcommand(name) {
  def run(conf : Conf)
}

object Main {
  def main(args : Array[String]) {
    val options = new Conf(args)
    options.subcommand match {
      case None => println("ERROR: No subcommand specified")
      case Some(m : Main) => m.run(options)
      case Some(other) => println("ERROR: Bad subcommand specified: " + other)
    }
  }
}