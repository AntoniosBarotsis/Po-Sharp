package scala

import fastparse.JavaWhitespace._
import fastparse._

object Parser {

  def topLevel[_: P]: P[Expr.TopLevel] = P((function | interfaceDef).rep(1)).map(x=>{
    println(x)
    var func: List[Expr.Func] = List()
    var intf: List[Expr.DefineInterface] = List()
    x.foreach{
      case y@Expr.Func(a,b,c,d) => func = func :+ y
      case y@Expr.DefineInterface(a,b) => intf = intf :+ y
    }
    Expr.TopLevel(func, intf)
  })
  def function[_: P]: P[Expr.Func] = P("def " ~/ ident ~ "(" ~/ functionArgs ~ ")" ~/ typeDef.? ~ block).map{
    case (name, args, retType, body) => {
      val ret = retType match {
        case Some(typ) => typ
        case None => Type.Undefined()
      }
      Expr.Func(name.name, args, ret, body)
    }
  }
  def interfaceDef[_: P]: P[Expr.DefineInterface] = P("interface " ~ ident ~/ "{" ~ (ident ~ typeDef).rep(sep = ",") ~ "}").map(props=>
    Expr.DefineInterface(props._1.name, props._2.toList.map(x=>InputVar(x._1.name, x._2)))
  )
  def functionArgs[_: P]: P[List[InputVar]] = P( (ident ~ typeDef).rep(sep = ",")).map(x=>x.map(y=>InputVar(y._1.name, y._2)).toList)
  //def parseType[_: P] : P[Type] = P(ident ~ "(" ~/ ")")
  def block[_: P] = P("{" ~/ line.rep(0) ~ "}").map ( lines => lines.foldLeft(List(): List[Expr])( (acc, el) => el match {
    case Expr.ExtendBlock(sub) => acc ::: sub;
    case _ => acc :+ el;
  } )).map(x=>Expr.Block(x))
  def line[_: P]: P[Expr] = P(expr ~/ ";")
  def expr[_: P]: P[Expr] = P( arrayDef | arrayDefDefault | defAndSetVal | defVal | setArray | setVal | setProp | retFunction | IfOp | whileLoop | forLoop | print | callFunction)

  def prefixExpr[_: P]: P[Expr] = P( NoCut(convert) | parens | arrayDef | arrayDefDefault | instanceInterface | getArray | getArraySize | getProp | callFunction | numberFloat | number | ident | constant | str | char)

  def defVal[_: P]: P[Expr.DefVal] = P("val " ~/ ident ~ typeDef.?).map{
    case(ident, Some(varType)) => Expr.DefVal(ident, varType)
    case(ident, None) => Expr.DefVal(ident, Type.Undefined())
  }
  //def setVal[_: P]: P[Expr.SetVal] = P(ident ~ "=" ~/ prefixExpr).map(x => Expr.SetVal(x._1, x._2))
  def setVal[_: P]: P[Expr.SetVal] = P(ident ~ StringIn("+=", "-=", "*=", "/=", "=").! ~/ prefixExpr).map(x => {
    val ret = x._2 match {
      case "=" => x._3
      case "+=" => Expr.Plus(x._1, x._3)
      case "-=" => Expr.Minus(x._1, x._3)
      case "*=" => Expr.Mult(x._1, x._3)
      case "/=" => Expr.Div(x._1, x._3)
    }
    Expr.SetVal(x._1, ret)
  })
  def defAndSetVal[_: P] = P( defVal ~ "=" ~ prefixExpr).map(x => Expr.ExtendBlock(List(x._1, Expr.SetVal(x._1.variable, x._2))))

  /*
  def arrayDef[_: P] = P("val " ~ ident ~ "=" ~ "Array" ~/ "[" ~ number ~ "]").map(x => {
    if(x._2.value < 0) throw new ParseException("negative array size");
    Expr.ExtendBlock(List(
      Expr.DefVal(x._1),
      Expr.SetVal(x._1, Expr.DefineArray(x._2.value))
    ));
  })
   */
  def arrayDef[_: P]: P[Expr.DefineArray] = P("array" ~ "[" ~/ typeBase ~ "]" ~ "[" ~/ prefixExpr ~ "]").map(x=> Expr.DefineArray(x._2, x._1, List()))
  def arrayDefDefault[_: P]: P[Expr.DefineArray] = P("array" ~ "(" ~/ prefixExpr.rep(sep = ",") ~ ")").map(x=> Expr.DefineArray(Expr.Num(x.size), Type.Undefined(), x.toList))

  def getArray[_: P]: P[Expr.GetArray] = P(ident ~ "[" ~/ prefixExpr ~ "]").map((x) => Expr.GetArray(x._1, x._2))
  def setArray[_: P]: P[Expr.SetArray] = P(ident ~ "[" ~/ prefixExpr ~ "]" ~/ "=" ~ prefixExpr).map((x) => Expr.SetArray(x._1, x._2, x._3))
  def getArraySize[_: P]: P[Expr.ArraySize] = P(ident ~~ ".size").map((x) => Expr.ArraySize(x))


  def instanceInterface[_: P]: P[Expr.InstantiateInterface] = P("new " ~/ ident ~ "{" ~/ prefixExpr.rep(sep = ",") ~ "}").map(x=>Expr.InstantiateInterface(x._1.name,x._2.toList))
  def returnsInterface[_: P] = P(NoCut(callFunction) | getArray | ident)
  def getProp[_: P]: P[Expr.GetInterfaceProp] = P(returnsInterface ~ "." ~/ ident).map(x=>Expr.GetInterfaceProp(x._1, x._2.name))
  def setProp[_: P]: P[Expr.SetInterfaceProp] = P(returnsInterface ~ "." ~/ ident ~ "=" ~ prefixExpr).map(x=>Expr.SetInterfaceProp(x._1,x._2.name, x._3))

  def typeDef[_: P]: P[Type] = P(":" ~/ (typeBase | typeArray | typeUser))
  def typeArray[_: P]: P[Type] =  P("array" ~/ "[" ~ typeBase ~ "]").map(x=>Type.Array(x))
  def typeUser[_: P]: P[Type] =  P(ident).map(x=>Type.UserType(x.name))
  def typeBase[_: P]: P[Type] = P(StringIn("int", "char", "float", "string", "void").!).map{
    case "int" => Type.Num();
    case "char" => Type.Character();
    case "float" => Type.NumFloat();
    case "string" => Type.Array(Type.Character());
    case "void" => Type.Undefined();
  }

  def parens[_: P] = P("(" ~/ (binOp | prefixExpr) ~ ")")
  def convert[_: P]: P[Expr.Convert] = P("(" ~ (binOp | prefixExpr) ~ ")" ~ "." ~/ StringIn("toInt", "toFloat", "toChar").!).map{
    case (value, "toInt") => Expr.Convert(value, Type.Num())
    case (value, "toFloat") => Expr.Convert(value, Type.NumFloat())
    case (value, "toChar") => Expr.Convert(value, Type.Character())
  }

  /*
  def binOp[_: P] = P(prefixExpr ~ StringIn("+", "-", "*", "/", "==", ">", "<").! ~/ prefixExpr).map({
    case (l, "+", r) => Expr.Plus(l, r)
    case (l, "-", r) => Expr.Minus(l, r)
    case (l, "*", r) => Expr.Mult(l, r)
    case (l, "/", r) => Expr.Div(l, r)
    case (l, "==", r) => Expr.Equals(l,r)
    case (l, "<", r) => Expr.LessThan(l,r)
    case (l, ">", r) => Expr.MoreThan(l,r)
    case _ => throw new ParseException("not bin p[")
  })
   */

  def callFunction[_: P]: P[Expr.CallF] = P( ident ~ "(" ~/ prefixExpr.rep(sep = ",") ~/ ")" ).map{
    case (name, args) => Expr.CallF(name.name, args.toList);
  }.filter((x) => !reservedKeywords.contains(x.name) )
  def retFunction[_: P]: P[Expr.Return] = P("return" ~/ prefixExpr.?).map(Expr.Return)

  def binOp[_: P] = P( prefixExpr ~ ( StringIn("+", "-", "*", "/", "++").! ~/ prefixExpr).rep(1)).map(list => parseBinOpList(list._1, list._2.toList))

  def parseBinOpList(initial: Expr, rest: List[(String, Expr)]): Expr = {
    rest.foldLeft(initial) {
      case (left, (operator, right)) => operator match {
        case "+" => Expr.Plus(left, right)
        case "-" => Expr.Minus(left, right)
        case "*" => Expr.Mult(left, right)
        case "/" => Expr.Div(left, right)
        case "++" => Expr.ConcatArray(left, right)
      }
    }
  }

  def IfOp[_: P]: P[Expr.If] = P("if" ~/ condition ~/ block ~/ elseOp.? ) .map{
    case (cond, ex_true, Some(ex_else)) => Expr.If(cond, ex_true, ex_else);
    case (cond, ex_true, None) => Expr.If(cond, ex_true, Expr.Block(List()));
  }

  def condition[_: P]: P[Expr] = P("(" ~/ conditionNoParen ~ ")")
  def conditionNoParen[_: P]: P[Expr] = P(negate | condOp | conditionBin | constant | condition )
  def negate[_: P]: P[Expr.Not] = P( "!" ~/ condition).map(Expr.Not)
  def conditionBin[_: P]: P[Expr] = P( prefixExpr ~ StringIn("==", "!=", ">", "<", "<=", ">=").! ~/ prefixExpr).map{
    case (left, operator, right) => operator match {
      case "==" => Expr.Equals(left, right)
      case "!=" => Expr.Not(Expr.Equals(left, right))
      case "<" => Expr.LessThan(left, right)
      case ">" => Expr.MoreThan(left, right)
      case "<=" => Expr.Not(Expr.MoreThan(left, right))
      case ">=" => Expr.Not(Expr.LessThan(left, right))
    }
  }

  def condOp[_: P]: P[Expr] = P(condition ~ ("&&" | "||").! ~/ condition ~ (("&&" | "||") ~/ condition).rep).map{
    case (first, "&&", second, rest) => Expr.And(List(first, second) ::: rest.toList)
    case (first, "||", second, rest) => Expr.Or(List(first, second) ::: rest.toList)
  }
  def elseOp[_: P] = P("else" ~/ block)

  def whileLoop[_: P]: P[Expr.While] = P("while" ~/ condition ~ block).map((input)=>Expr.While(input._1, input._2))
  def forLoop[_: P]: P[Expr.Block] = P("for" ~/ "(" ~ line ~ conditionNoParen ~ ";" ~ line ~ ")" ~/ block).map((input)=> {
    Expr.Block(List( input._1, Expr.While(input._2, Expr.Block(input._4.lines :+ input._3))));
  })

  def str[_: P]: P[Expr] = P("\"" ~~/ CharsWhile(_ != '"', 0).! ~~ "\"").map(x=>Expr.Str(x+"\u0000"))
  def ident[_: P]: P[Expr.Ident] = P(CharIn("a-zA-Z_") ~~ CharsWhileIn("a-zA-Z0-9_", 0)).!.map((input) => {
    Expr.Ident(input)
  }).filter((x) => !reservedKeywords.contains(x) )
  def number[_: P]: P[Expr.Num] = P( "-".!.? ~~ CharsWhileIn("0-9", 1)).!.map(x => Expr.Num(Integer.parseInt(x)))
  def numberFloat[_: P]: P[Expr.NumFloat] = P( "-".? ~~ CharsWhileIn("0-9", 1) ~~ "." ~~ CharsWhileIn("0-9", 1)).!.map(x => Expr.NumFloat(x.toFloat))
  def char[_: P]: P[Expr.Character] = P("'" ~/ AnyChar.! ~/ "'").map(x=>Expr.Character(x.charAt(0)));
  def constant[_: P]: P[Expr] = P( trueC | falseC )
  def trueC[_: P]: P[Expr.True] = P("true").map(_ => Expr.True())
  def falseC[_: P]: P[Expr.False] = P("false").map(_ => Expr.False())

  def print[_: P]: P[Expr.Print] = P("print" ~ "(" ~/ ( NoCut(binOp) | prefixExpr ) ~ ")").map(Expr.Print)

  class ParseException(s: String) extends RuntimeException(s)
  val reservedKeywords = List("def", "val", "if", "while", "true", "false", "array", "for", "print", "interface")
  def checkForReservedKeyword(input: Expr.Ident): Unit ={
    if(reservedKeywords.contains(input.name)) throw new ParseException(s"${input.name} is a reserved keyword");
  }

  def parseInput(input: String): Expr = {
    val parsed = fastparse.parse(input, topLevel(_));
    parsed match {
      case Parsed.Success(expr, n) => expr;
      case t: Parsed.Failure => {println(t.trace().longAggregateMsg); throw new ParseException("parsing fail");}
      case _ => throw new ParseException("parsing fail")
    }
  }
}
