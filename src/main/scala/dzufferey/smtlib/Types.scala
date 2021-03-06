package dzufferey.smtlib

import dzufferey.utils.Logger
import dzufferey.utils.LogLevel._

sealed abstract class Type {
  def freeParameters: Set[TypeVariable]
  def alpha(subst: Map[TypeVariable, Type]): Type
  def arity = 0
  
  //syntactic sugar
  def ~>(that: Type): Function = this match {
    case Function(args, ret) => Function(args ::: List(ret), that)
    case other => Function(List(this), that)
  }
}

object Type {

  private val counter = new java.util.concurrent.atomic.AtomicInteger()

  def freshTypeVar = {
    val cnt = counter.incrementAndGet()
    TypeVariable("_" + cnt)
  }

  def freshParams(tpe: Type): (Map[TypeVariable,TypeVariable], Type) = {
    var oldParams = tpe.freeParameters
    var subst: Map[TypeVariable,TypeVariable] = (for (t <- oldParams.toSeq) yield (t, freshTypeVar)).toMap
    (subst, tpe alpha subst)
  }

  def unify(t1: Type, t2: Type): Option[Map[TypeVariable, Type]] = (t1,t2) match {
    case (a,b) if a == b =>
      Some(Map.empty[TypeVariable, Type])
    case (Wildcard, _) | (_, Wildcard) =>
      Some(Map.empty[TypeVariable, Type])
    case (v1 @ TypeVariable(n1), v2 @ TypeVariable(n2)) =>
      if (n1 == n2) Some(Map.empty[TypeVariable, Type])
      else if (n1 < n2) Some(Map(v1 -> v2))
      else Some(Map(v2 -> v1))
    case (v1 @ TypeVariable(_), otherType) if !otherType.freeParameters.contains(v1) =>
      Some(Map(v1 -> otherType))
    case (otherType, v1 @ TypeVariable(_)) if !otherType.freeParameters.contains(v1) =>
      Some(Map(v1 -> otherType))
    case (UnInterpreted(i1), UnInterpreted(i2)) if i1 == i2 =>
      Some(Map.empty[TypeVariable, Type])
    case (Function(arg1, r1), Function(arg2, r2)) if arg1.size == arg2.size =>
      arg1.zip(arg2).foldLeft(Some(Map.empty[TypeVariable, Type]): Option[Map[TypeVariable, Type]])( (acc, p) => {
        acc.flatMap( map => {
          val t1 = p._1.alpha(map)
          val t2 = p._2.alpha(map)
          unify(t1, t2).map(_ ++ map)
        })
      })
    case _ =>
      Logger("Typer", Warning, "failed to unify: " + t1 + " ⇔ " + t2)
      None
  }

}

case object Bool extends Type {
  override def toString = "Bool"
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this 
}

case object Int extends Type {
  override def toString = "Int"
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this 
}

case object Real extends Type {
  override def toString = "Real"
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this 
}

case object Wildcard extends Type {
  override def toString = "_"
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this 
}

case class Function(args: List[Type], returns: Type) extends Type {
  override def toString = args.mkString("(","->","->") + returns + ")"
  override def arity = args.length
  def freeParameters = (returns.freeParameters /: args)(_ ++ _.freeParameters)
  def alpha(subst: Map[TypeVariable, Type]) = Function(args.map(_.alpha(subst)), returns.alpha(subst)) 
}

case class UnInterpreted(id: String) extends Type {
  override def toString = id
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this 
}

case class TypeVariable(name: String) extends Type {
  override def toString = "'"+name
  def freeParameters = Set[TypeVariable](this)
  def alpha(subst: Map[TypeVariable, Type]) = subst.getOrElse(this, this)
}

case object IArray extends Type {
  override def toString = "(Array Int Int)"
  def freeParameters = Set[TypeVariable]()
  def alpha(subst: Map[TypeVariable, Type]) = this
}
