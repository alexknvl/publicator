package io.steamcraft.publicator

import java.io.File
import java.nio.file.Files
import java.util.jar.{JarEntry, JarFile, JarOutputStream}

import cats.Eval
import cats.data.NonEmptyList
import com.google.common.io.ByteStreams
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.{ClassReader, ClassWriter, Opcodes}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

trait RuleAlg[R] {
  def and(a: R, b: R): R
  def or(a: R, b: R): R
  def not(a: R): R
  def prefix(s: String): R
}
object RuleAlg {
  sealed trait Free {
    def fold[R](alg: RuleAlg[R]): R = this match {
      case Free.And(a, b) => alg.and(a.fold(alg), b.fold(alg))
      case Free.Or(a, b) => alg.or(a.fold(alg), b.fold(alg))
      case Free.Not(a) => alg.not(a.fold(alg))
      case Free.Prefix(s) => alg.prefix(s)
    }
  }
  object Free {
    final case class And(a: Free, b: Free) extends Free
    final case class Or(a: Free, b: Free) extends Free
    final case class Not(a: Free) extends Free
    final case class Prefix(a: String) extends Free
  }

  val freeAlg: RuleAlg[Free] = new RuleAlg[Free] {
    override def and(a: Free, b: Free): Free = Free.And(a, b)
    override def or(a: Free, b: Free): Free = Free.Or(a, b)
    override def not(a: Free): Free = Free.Not(a)
    override def prefix(s: String): Free = Free.Prefix(s)
  }

  val predicateAlg: RuleAlg[String => Boolean] = new RuleAlg[String => Boolean] {
    override def and(a: String => Boolean, b: String => Boolean): String => Boolean =
      x => a(x) && b(x)
    override def or(a: String => Boolean, b: String => Boolean): String => Boolean =
      x => a(x) || b(x)
    override def not(a: String => Boolean): String => Boolean = x => !a(x)
    override def prefix(s: String): String => Boolean = x => x.startsWith(s)
  }

  def parser[R](alg: RuleAlg[R]): String => Either[String, R] = {
    import atto._
    import Atto._
    import scala.Predef.charWrapper

    val and = token(string("&")).namedOpaque("AND")
    val or  = token(string("|")).namedOpaque("OR")
    val not = token(string("!")).namedOpaque("NOT")
    def pathSymbols(ch: Char): Boolean = ch.isLetterOrDigit ||
      ch == '_' || ch == '$' || ch == '/'

    lazy val orExpr: Parser[R] = andExpr.sepBy1(or).map(_.reduceLeft(alg.or))
    lazy val andExpr: Parser[R] = value.sepBy1(and).map(_.reduceLeft(alg.and))
    lazy val value: Parser[R] = (
      (token(string("(")) ~> orExpr <~ token(string(")"))).named("paren")
        | (not ~> value).map(alg.not).named("not")
        | token(stringLiteral.map(alg.prefix)).namedOpaque("string")
        | token(takeWhile(pathSymbols)).map(alg.prefix).namedOpaque("raw")
      )

    val line = skipWhitespace ~> orExpr <~ skipWhitespace

    s => line.parseOnly(s).either
  }
}

object App {
  def using[R, Z](init: => R)(release: R => Unit)(body: R => Z): Option[Z] = {
    var r: Option[R] = None
    try {
      r = Some(init)
      Some(body(r.get))
    } catch {
      case NonFatal(e) =>
        r match {
          case None => ()
          case Some(res) =>
            release(res)
        }
        None
    }
  }

  val parser = RuleAlg.parser(RuleAlg.freeAlg)

  def main(args: Array[String]): Unit = args match {
    case Array(in, out, ruleStr) =>
      val jarIn = new File(in)
      val jarOutPath = new File(out)

      parser(ruleStr) match {
        case Left(error) =>
          Console.println("ERROR: " + error)

        case Right(rule) =>
          Console.println(rule.toString)
          val predicate = rule.fold(RuleAlg.predicateAlg)

          using(new JarFile(jarIn))(_.close()) { jarFile =>
            using(new JarOutputStream(Files.newOutputStream(jarOutPath.toPath)))(_.close()) { jos =>
              jarFile.stream.forEach({ entry =>
                val is = jarFile.getInputStream(entry)
                val entryName = entry.getName

                if (entryName.endsWith(".class") && predicate(entryName)) {
                  Console.println(entryName)

                  val reader = new ClassReader(ByteStreams.toByteArray(is))
                  val node = new ClassNode
                  reader.accept(node, 0)

                  node.access &= ~Opcodes.ACC_FINAL
                  node.access &= ~Opcodes.ACC_PRIVATE
                  node.access &= ~Opcodes.ACC_PROTECTED
                  node.access |= Opcodes.ACC_PUBLIC

                  node.methods.asScala.foreach { m =>
                    m.access &= ~Opcodes.ACC_FINAL
                    m.access &= ~Opcodes.ACC_PRIVATE
                    m.access &= ~Opcodes.ACC_PROTECTED
                    m.access |= Opcodes.ACC_PUBLIC
                  }

                  node.fields.asScala.foreach { m =>
                    m.access &= ~Opcodes.ACC_PRIVATE
                    m.access &= ~Opcodes.ACC_PROTECTED
                    m.access |= Opcodes.ACC_PUBLIC
                  }

                  node.innerClasses.asScala.foreach { m =>
                    node.access &= ~Opcodes.ACC_FINAL
                    m.access &= ~Opcodes.ACC_PRIVATE
                    m.access &= ~Opcodes.ACC_PROTECTED
                    m.access |= Opcodes.ACC_PUBLIC
                  }

                  val writer = new ClassWriter(0)
                  node.accept(writer)
                  jos.putNextEntry(new JarEntry(entryName))
                  jos.write(writer.toByteArray)
                }
                else {
                  jos.putNextEntry(new JarEntry(entryName))
                  jos.write(ByteStreams.toByteArray(is))
                }
              })
            }
          }
      }
  }
}
