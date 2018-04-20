package org.ucombinator.jaam.util

import soot.{SootMethod, Value}
import soot.jimple.{Stmt => SootStmt, _}
import soot.jimple.toolkits.annotation.logic.{Loop => SootLoop}
import soot.toolkits.graph.LoopNestTree

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

object Loop {
  def getLoopSet(method: SootMethod, skipExceptionLoops: Boolean = true): Set[SootLoop] = {
    val lnt = new LoopNestTree(Soot.getBody(method))
    val infoSet = lnt.toSet[SootLoop].map(loop => identifyLoop(loop, method)).filterNot(skipExceptionLoops && _.isInstanceOf[ExceptionLoop])
    infoSet.map(_.loop)
  }

  def identifyLoop(loop: SootLoop, method: SootMethod): LoopInfo = {
    // TODO: Identify nested loops.
    if (loop.loopsForever()) {
      return SimpleInfiniteLoop(loop, method)
    }
    loop.getHead match {
      case _: IfStmt =>
        if (loop.getLoopExits.size() == 0) {
          ExitlessLoop(loop, method)
        }
        RegularLoop(loop, method)
      case s: DefinitionStmt =>
        s.getRightOp match {
          case rhs: InterfaceInvokeExpr =>
            /**
              * Assumption: if the rhs of an assignment in the first statement of a loop is an InterfaceInvokeExpr, and
              * if the class being invoked from is "java.util.Iterator", then this loop is something like a for-each
              * loop and should be handled as such.
              */
            if (rhs.getMethod.getDeclaringClass.equals(Soot.classes.Iterator)) {
              IteratorLoop(loop, method)
            } else {
              InterfaceInvokeLoop(loop, method)
            }
          case _: CaughtExceptionRef =>
            /**
              * Assumption: these are the not-really-a-loop loops generated by having enough variables in a block with
              * a try/catch/finally.
              */
            ExceptionLoop(loop, method)
          case _ =>
            UnclassifiedAssignmentLoop(loop, method)
        }
      case _: InterfaceInvokeExpr =>
        IteratorLoop(loop, method)
      case _ =>
        UnidentifiedLoop(loop, method)
    }
  }

  def getAssignees(statements: java.util.List[SootStmt]): Set[Value] = {
    // Get list of all values assigned to in a set of statements.
    statements.asScala.toSet.filter(s => s.isInstanceOf[AssignStmt]).map(s => s.asInstanceOf[AssignStmt].getLeftOp)
  }

  abstract class LoopInfo(val loop: SootLoop, val method: SootMethod) {
    val head: Stmt = Stmt(loop.getHead, method)
    val prehead: Stmt = head.prevSyntactic
    val exits: Int = loop.getLoopExits.size()
    val assignees: Set[Value] = Loop.getAssignees(loop.getLoopStatements)
  }
  case class UnidentifiedLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class SimpleInfiniteLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class ExitlessLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class RegularLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method) {
    val cond: ConditionExpr = loop.getHead.asInstanceOf[IfStmt].getCondition.asInstanceOf[ConditionExpr]
    val op1: Value = cond.getOp1
    val op2: Value = cond.getOp2
  }
  case class IteratorLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class UnclassifiedAssignmentLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class InterfaceInvokeLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)
  case class ExceptionLoop(override val loop: SootLoop, override val method: SootMethod) extends LoopInfo(loop, method)

}
