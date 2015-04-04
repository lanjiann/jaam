package org.ucombinator.analyzer

/*
  Warning:

  1. Don't cross the streams.  It would be bad.

  2. Don't order addresses.  Dogs and cats, living together.  Mass hysteria.
*/

// TODO: why haven't we seen phi nodes?
// TODO: need to track exceptions that derefing a Null could cause

import org.ucombinator.SootWrapper
import scala.collection.JavaConversions._
import scala.language.postfixOps
import soot.util.Chain
import soot.SootClass
import soot.SootField
import soot.SootMethod
import soot.SootMethodRef

// We expect every Unit we use to be a soot.jimple.Stmt, but the APIs
// are built around using Unit so we stick with that.  (We may want to
// fix this when we build the Scala wrapper for Soot.)
import soot.{Unit => SootUnit}
import soot.jimple.{Stmt => SootStmt}

import soot.Local
import soot.{Value => SootValue}
import soot.IntType
import soot.jimple._

import soot.jimple.internal.JStaticInvokeExpr
import soot.jimple.toolkits.invoke.AccessManager

case class UninitializedClassException(sootClass : SootClass) extends RuntimeException

abstract class FramePointer

abstract class BasePointer

class ConcreteFramePointer() extends FramePointer

class ConcreteBasePointer() extends BasePointer

object InvariantFramePointer extends FramePointer

object InvariantBasePointer extends BasePointer

case class KontStack(store : KontStore, k : Kont) {
  def push(frame : Frame) : KontStack = {
    // TODO replace InvariantKontAddr with call to parameterized function
    val kAddr = InvariantKontAddr
    val newKontStore = store.update(kAddr, Set(k))
    KontStack(newKontStore, RetKont(frame, kAddr))
  }

  def pop() : Set[(Frame, KontStack)] = {
    k match {
      case RetKont(frame, kontAddr) => {
        for (topk <- store(kontAddr)) yield (frame, KontStack(store, topk))
        }
      case HaltKont => Set()
    }
  }
}

abstract class KontAddr

case object InvariantKontAddr extends KontAddr

// TODO Michael B: refactor KontStore and Store, since they only
// differ in their types
case class KontStore(private val map : Map[KontAddr, Set[Kont]]) {
  def update(addr : KontAddr, konts : Set[Kont]) : KontStore = {
    map.get(addr) match {
      case Some(oldd) => KontStore(map + (addr -> (oldd ++ konts)))
      case None => KontStore(map + (addr -> konts))
    }
  }

  def apply(addr : KontAddr) : Set[Kont] = map(addr)
  def get(addr : KontAddr) : Option[Set[Kont]] = map.get(addr)
}


case class Frame(
  val stmt : Stmt,
  val fp : FramePointer,
  val destAddr : Option[Set[Addr]]) {

  def acceptsReturnValue() : Boolean = !(destAddr.isEmpty)
}

abstract class Kont

case class RetKont(
  val frame : Frame,
  val k : KontAddr
) extends Kont

object HaltKont extends Kont

case class D(val values: Set[Value]) {
  def join(otherd : D) : D = {
    D(values ++ otherd.values)
  }
}

object D {
  val atomicTop = D(Set(AnyAtomicValue))
}

abstract class Value

abstract class AtomicValue extends Value

case object AnyAtomicValue extends AtomicValue

case class ObjectValue(val sootClass : SootClass,  val bp : BasePointer) extends Value

abstract class Addr

abstract class FrameAddr extends Addr

case class Store(private val map : Map[Addr, D]) {
  def update(addr : Addr, d : D) : Store = {
    map.get(addr) match {
      case Some(oldd) => Store(map + (addr -> oldd.join(d)))
      case None => Store(map + (addr -> d))
    }
  }
  def update(addrs : Set[Addr], d : D) : Store = {
     var newStore = this
     for (a <- addrs) {
       newStore = newStore.update(a, d)
     }
     newStore
  }

  def apply(addr : Addr) : D = map(addr)
  def apply(addrs : Set[Addr]) : D = {
    val ds = for (a <- addrs)
               yield map(a)
    ds.fold (D(Set()))(_ join _)
  }
  def get(addr : Addr) : Option[D] = map.get(addr)
}

case class LocalFrameAddr(val fp : FramePointer, val register : Local) extends FrameAddr

case class ParameterFrameAddr(val fp : FramePointer, val parameter : Int) extends FrameAddr

case class ThisFrameAddr(val fp : FramePointer) extends FrameAddr

case class InstanceFieldAddr(val bp : BasePointer, val sf : SootField) extends Addr

case class StaticFieldAddr(val sf : SootField) extends Addr

case class Stmt(val unit : SootUnit, val method : SootMethod, val program : Map[String, SootClass]) {
  assert(unit.isInstanceOf[SootStmt])
  def nextSyntactic() : Stmt = this.copy(unit = method.getActiveBody().getUnits().getSuccOf(unit))
  override def toString() : String = unit.toString()
}

case class State(stmt : Stmt,
                 fp : FramePointer,
                 store : Store,
                 kontStack : KontStack,
                 initializedClasses : Set[SootClass]) {
  def alloca() : FramePointer = InvariantFramePointer

  def addrsOf(v : SootValue) : Set[Addr] = {
    // TODO missing: ArrayRef, CaughtExceptionRef
    v match {
      case v : Local => Set(LocalFrameAddr(fp, v))
      case v : InstanceFieldRef => {
        val b = v.getBase()
        val d = eval(b)
        // TODO/optimize
        // filter out incorrect class types
        for (x <- d.values if x.isInstanceOf[ObjectValue])
         yield InstanceFieldAddr(x.asInstanceOf[ObjectValue].bp, v.getField())
      }
      case v : StaticFieldRef => {
        val f = v.getField()
        val c = f.getDeclaringClass()
        if (initializedClasses.contains(c)) {
          Set(StaticFieldAddr(f))
        } else {
          throw new UninitializedClassException(c)
        }
      }
      case v : ParameterRef => Set(ParameterFrameAddr(fp, v.getIndex()))
      case v : ThisRef => Set(ThisFrameAddr(fp))
    }
  }

  def eval(v: SootValue) : D = {
    v match {
      // TODO missing: BinopExpr(...), UnopExpr(...), Immediate(...), CastExpr, InstanceOfExpr
      case (_ : Local) | (_ : Ref) => store(addrsOf(v))
      case _ : NullConstant => D.atomicTop
      case n : NumericConstant => D.atomicTop
      case subexpr : SubExpr => {
        assert(subexpr.getOp1().getType().isInstanceOf[IntType])
        assert(subexpr.getOp2().getType().isInstanceOf[IntType])

        D.atomicTop
      }

      case subexpr : MulExpr => {
        assert(subexpr.getOp1().getType().isInstanceOf[IntType])
        assert(subexpr.getOp2().getType().isInstanceOf[IntType])

        D.atomicTop
      }

      case _ =>  throw new Exception("No match for " + v.getClass + " : " + v)
    }
  }

  // The last parameter of handleInvoke allows us to override what
  // Stmt to execute after returning from this call.  We need this for
  // static class initialization because in that case we want to
  // return to the current statement instead of the next statement.
  def handleInvoke(expr : InvokeExpr, destAddr : Option[Set[Addr]], nextStmt : Stmt = stmt.nextSyntactic()) : Set[State] = {
    val methRef = expr.getMethodRef
    val newFP = alloca()
    var newStore = store
    for (i <- 0 until expr.getArgCount())
      newStore = newStore.update(ParameterFrameAddr(newFP, i), eval(expr.getArg(i)))
    val newKontStack = kontStack.push(Frame(nextStmt, newFP, destAddr))

    def dispatch(c : SootClass, ref : SootMethodRef) : Set[State] = {
      def overloads(curr : SootClass, root_m : SootMethod) : List[SootMethod] = {
        val curr_m = curr.getMethodUnsafe(root_m.getName, root_m.getParameterTypes, root_m.getReturnType)
        if (curr_m == null) { overloads(curr.getSuperclass(), root_m) }
        else if (AccessManager.isAccessLegal(curr_m, root_m)) { List(curr_m) }
        else {
          val o = overloads(curr.getSuperclass(), root_m)
          (if (o.exists(m => AccessManager.isAccessLegal(curr_m, m))) List(curr_m) else List()) ++ o
        }
      }

      val meth = if (c != null) overloads(c, ref.resolve()).head
                 else ref.declaringClass.getMethod(ref.name, ref.parameterTypes, ref.returnType)
      Snowflakes.get(meth) match {
        case Some(h) => h(this, nextStmt, newFP, newStore, newKontStack)
        case None =>
          Set(State(Stmt(meth.getActiveBody().getUnits().getFirst, meth, stmt.program),
            newFP, newStore, newKontStack, initializedClasses))
      }
    }

    expr match {
      case expr : DynamicInvokeExpr => ??? // TODO: Could only come from non-Java sources
      case expr : StaticInvokeExpr => dispatch(null, methRef)
      case expr : InstanceInvokeExpr =>
        val th = ThisFrameAddr(newFP)
        val d = eval(expr.getBase())
        // TODO/optimize: filter out incorrect class types
        for (v@ObjectValue(sootClass, _) <- d.values)
          newStore = newStore.update(th, D(Set(v)))
        expr match {
          case expr : SpecialInvokeExpr => dispatch(null, methRef)
          case expr : VirtualInvokeExpr =>
            ((for (ObjectValue(sootClass, _) <- d.values) yield
              dispatch(sootClass, methRef)) :\ Set[State]())(_ ++ _) // TODO: better way to do this?
          case expr : InstanceInvokeExpr => ??? // TODO
        }
    }
  }

  def next() : Set[State] = {
    try {
      true_next()
    } catch {
      case UninitializedClassException(sootClass) =>
        // TODO: exception needs to be called on *all* class accesses (including instance fields and methods)
        this.copy(initializedClasses = initializedClasses + sootClass)
          .handleInvoke(new JStaticInvokeExpr(sootClass.getMethodByName("<clinit>").makeRef(),
                                              java.util.Collections.emptyList()), None, stmt)
    }
  }

  def true_next() : Set[State] = {
    stmt.unit match {
      case unit : InvokeStmt => handleInvoke(unit.getInvokeExpr, None)

      case unit : DefinitionStmt => {
        val lhsAddr = addrsOf(unit.getLeftOp())

        unit.getRightOp() match {
          // TODO missing: NewArrayExpr, NewMultiArrayExpr
          case rhs : InvokeExpr => handleInvoke(rhs, Some(lhsAddr))
          case rhs : NewExpr => {
            val baseType = rhs.getBaseType()
            val sootClass = baseType.getSootClass()
            val bp = InvariantBasePointer // TODO turn this into malloc
            val obj = ObjectValue(sootClass, bp)
            val d = D(Set(obj))
            val newStore = store.update(lhsAddr, d)
            Set(State(stmt.nextSyntactic(), fp, newStore, kontStack, initializedClasses))
          }
          case rhs => {
            val evaledRhs = eval(rhs)
            val newStore = store.update(lhsAddr, evaledRhs)
            Set(State(stmt.nextSyntactic(), fp, newStore, kontStack, initializedClasses))
          }
        }
      }

      case unit : IfStmt => {
            val trueState = this.copy(stmt = stmt.copy(unit = unit.getTarget()))
            val falseState = this.copy(stmt = stmt.nextSyntactic())
        Set(trueState, falseState)
      }

      // TODO: needs testing
      case unit : SwitchStmt =>
      unit.getTargets().map(t => this.copy(stmt = stmt.copy(unit = t))).toSet

      case unit : ReturnStmt => {
        val evaled = eval(unit.getOp())
        for ((frame, newStack) <- kontStack.pop) yield {
          val newStore = if (frame.acceptsReturnValue()) {
            store.update(frame.destAddr.get, evaled)
          } else {
            store
          }

          State(frame.stmt, frame.fp, newStore, newStack, initializedClasses)
        }
      }

      case unit : ReturnVoidStmt => {
        for ((frame, newStack) <- kontStack.pop if !(frame.acceptsReturnValue()))
            yield State(frame.stmt, frame.fp, store, newStack, initializedClasses)
      }

      // Since Soot's NopEliminator run before us, no "nop" should be
      // left in the code and this case isn't needed (and also is
      // untested).  The one place a "nop" could occur is as the last
      // instruction of a method that is also the instruction after
      // the end of a "try" clause. (See NopEliminator for the exact
      // conditions.) However, that would not be an executable
      // instruction, so we still wouldn't need this case.
      //
      // If we ever need the code for this, it would probably be:
      //   Set(State(stmt.nextSyntactic(), fp, store, kontStack, initializedClasses))
      case unit : NopStmt => throw new Exception("Impossible statement: " + unit)

      case unit : GotoStmt => Set(this.copy(stmt = stmt.copy(unit = unit.getTarget())))

      // We're missing BreakPointStmt, MonitorStmt, RetStmt, and ThrowStmt.

      case _ => {
        throw new Exception("No match for " + stmt.unit.getClass + " : " + stmt.unit)
      }
    }
  }
}

object State {
  def inject(stmt : Stmt) : State = {
    val initial_map : Map[Addr, D] = Map((ParameterFrameAddr(InvariantFramePointer, 0) -> D.atomicTop))
    State(stmt, InvariantFramePointer, Store(initial_map), KontStack(KontStore(Map()), HaltKont), Set())
  }
}

case class MethodDescription(val className : String,
                             val methodName : String,
                             val parameterTypes : List[String],
                             val returnType : String)

abstract class SnowflakeHandler {
  def apply(state : State,
            nextStmt : Stmt,
            newFP : FramePointer,
            newStore : Store,
            newKontStack : KontStack) : Set[State]
}

object Snowflakes {
  val table = scala.collection.mutable.Map.empty[MethodDescription, SnowflakeHandler]
  def get(meth : SootMethod) : Option[SnowflakeHandler] =
    table.get(MethodDescription(
      meth.getDeclaringClass.getName,
      meth.getName,
      meth.getParameterTypes.toList.map(_.toString()),
      meth.getReturnType.toString()))
  def put(md : MethodDescription, handler : SnowflakeHandler) : Unit = table.put(md, handler)
}

object NoOpSnowflake extends SnowflakeHandler {
  override def apply(state : State,
                     nextStmt : Stmt,
                     newFP : FramePointer,
                     newStore : Store,
                     newKontStack : KontStack) : Set[State] =
    Set(state.copy(stmt = nextStmt))
}

object Main {
  def main(args : Array[String]) {
    // TODO: proper option parsing
    if (args.length != 3) println("Expected arguments: [classDirectory] [className] [methodName]")
    val classDirectory = args(0)
    val className = args(1)
    val methodName = args(2)

    val source = SootWrapper.fromClasses(classDirectory, "")
    val classes = getClassMap(source.getShimple())

    Snowflakes.put(MethodDescription("java.lang.System","registerNatives",List(),"void"),
                   NoOpSnowflake)
    val mainMainMethod = classes(className).getMethodByName(methodName);
    val units = mainMainMethod.getActiveBody().getUnits();

    val first = units.getFirst()

    val initialState = State.inject(Stmt(first, mainMainMethod, classes))
    var todo : List [State] = List(initialState)
    var seen : Set [State] = Set()
    while (todo nonEmpty) {
      val current = todo.head
      println(current)
      println()
      val nexts = current.next
      // TODO: Fix optimization bug here
      todo = nexts.toList.filter(!seen.contains(_)) ++ todo.tail
      seen = seen ++ nexts
    }
  }

  def getClassMap(classes : Chain[SootClass]) : Map[String, SootClass] =
    (for (c <- classes) yield c.getName() -> c).toMap
}
