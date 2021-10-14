/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.akkasls.codegen

import java.nio.file.Path
import java.nio.file.Paths

import scala.annotation.tailrec
import scala.collection.immutable

import com.lightbend.akkasls.codegen.ModelBuilder.Command
import com.lightbend.akkasls.codegen.ModelBuilder.MessageTypeArgument
import com.lightbend.akkasls.codegen.ModelBuilder.ScalarType
import com.lightbend.akkasls.codegen.ModelBuilder.ScalarTypeArgument
import com.lightbend.akkasls.codegen.ModelBuilder.State
import com.lightbend.akkasls.codegen.ModelBuilder.TypeArgument

object SourceGeneratorUtils {
  val managedComment =
    """// This code is managed by Akka Serverless tooling.
       |// It will be re-generated to reflect any changes to your protobuf definitions.
       |// DO NOT EDIT""".stripMargin

  val unmanagedComment =
    """// This class was initially generated based on the .proto definition by Akka Serverless tooling.
       |//
       |// As long as this file exists it will not be overwritten: you can maintain it yourself,
       |// or delete it so it is regenerated as needed.""".stripMargin

  def mainPackageName(classNames: Iterable[String]): List[String] = {
    val packages = classNames
      .map(
        _.replaceFirst("\\.[^.]*$", "")
          .split("\\.")
          .toList)
      .toSet
    if (packages.isEmpty) throw new IllegalArgumentException("Nothing to generate!")
    longestCommonPrefix(packages.head, packages.tail)
  }

  @tailrec
  def longestCommonPrefix(
      reference: List[String],
      others: Set[List[String]],
      resultSoFar: List[String] = Nil): List[String] = {
    reference match {
      case Nil =>
        resultSoFar
      case head :: tail =>
        if (others.forall(p => p.headOption.contains(head)))
          longestCommonPrefix(tail, others.map(_.tail), resultSoFar :+ head)
        else
          resultSoFar
    }

  }

  def disassembleClassName(fullClassName: String): (String, String) = {
    val className = fullClassName.reverse.takeWhile(_ != '.').reverse
    val packageName = fullClassName.dropRight(className.length + 1)
    packageName -> className
  }

  def qualifiedType(fullyQualifiedName: FullyQualifiedName): String =
    if (fullyQualifiedName.parent.javaMultipleFiles) fullyQualifiedName.name
    else s"${fullyQualifiedName.parent.javaOuterClassname}.${fullyQualifiedName.name}"

  def typeImport(fullyQualifiedName: FullyQualifiedName): String = {
    val name =
      if (fullyQualifiedName.parent.javaMultipleFiles) fullyQualifiedName.name
      else if (fullyQualifiedName.parent.javaOuterClassnameOption.nonEmpty) fullyQualifiedName.parent.javaOuterClassname
      else fullyQualifiedName.name
    s"${fullyQualifiedName.parent.javaPackage}.$name"
  }

  def lowerFirst(text: String): String =
    text.headOption match {
      case Some(c) => c.toLower.toString + text.drop(1)
      case None    => ""
    }

  def packageAsPath(packageName: String): Path =
    Paths.get(packageName.replace(".", "/"))

  def generateImports(
      types: Iterable[FullyQualifiedName],
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty): Imports = {
    val messageTypeImports = types
      .filterNot { typ =>
        typ.parent.javaPackage == packageName
      }
      .filterNot { typ =>
        typ.parent.javaPackage.isEmpty
      }
      .filterNot { typ =>
        packageImports.contains(typ.parent.javaPackage)
      }
      .map(typeImport)

    new Imports(packageName, (messageTypeImports ++ otherImports ++ packageImports).toSeq.distinct.sorted)
  }

  def generateCommandImports(
      commands: Iterable[Command],
      state: State,
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty): Imports = {
    val types = commandTypes(commands) :+ state.fqn
    generateImports(types, packageName, otherImports, packageImports)
  }

  def generateCommandAndTypeArgumentImports(
      commands: Iterable[Command],
      typeArguments: Iterable[TypeArgument],
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty): Imports = {

    val types = commandTypes(commands) ++
      typeArguments.collect { case MessageTypeArgument(fqn) => fqn }

    generateImports(types, packageName, otherImports ++ extraTypeImports(typeArguments), packageImports)
  }

  def extraTypeImports(typeArguments: Iterable[TypeArgument]): Seq[String] =
    typeArguments.collect { case ScalarTypeArgument(ScalarType.Bytes) =>
      "com.google.protobuf.ByteString"
    }.toSeq

  def commandTypes(commands: Iterable[Command]): Seq[FullyQualifiedName] =
    commands.flatMap(command => Seq(command.inputType, command.outputType)).toSeq

  def collectRelevantTypes(
      fullQualifiedNames: Iterable[FullyQualifiedName],
      service: FullyQualifiedName): immutable.Seq[FullyQualifiedName] = {
    fullQualifiedNames.filterNot { desc =>
      desc.parent == service.parent
    }.toList
  }

  def collectRelevantTypeDescriptors(
      fullQualifiedNames: Iterable[FullyQualifiedName],
      service: FullyQualifiedName): String = {
    collectRelevantTypes(fullQualifiedNames, service)
      .map(desc => s"${desc.parent.javaOuterClassname}.getDescriptor()")
      .distinct
      .sorted
      .mkString(",\n")
  }

  def extraReplicatedImports(replicatedData: ModelBuilder.ReplicatedData): Seq[String] = {
    replicatedData match {
      // special case ReplicatedMap as heterogeneous with ReplicatedData values
      case _: ModelBuilder.ReplicatedMap => Seq("com.akkaserverless.replicatedentity.ReplicatedData")
      case _                             => Seq.empty
    }
  }

  @tailrec
  def dotsToCamelCase(s: String, resultSoFar: String = "", capitalizeNext: Boolean = true): String = {
    if (s.isEmpty) resultSoFar
    else if (s.head == '.' || s.head == '_' || s.head == '-') dotsToCamelCase(s.tail, resultSoFar, true)
    else if (capitalizeNext) dotsToCamelCase(s.tail, resultSoFar + s.head.toUpper, false)
    else dotsToCamelCase(s.tail, resultSoFar + s.head, false)
  }

  class CodeBlock(val code: Seq[Any]) {
    code.foreach(validateType)

    private def validateType(seq: Any): Unit = seq match {
      case _: String             => ()
      case _: FullyQualifiedName => ()
      case block: CodeBlock      => block.code.foreach(validateType)
      case s: Seq[_]             => s.foreach(validateType)
      case other =>
        throw new IllegalArgumentException(s"Unexpected value of type [${other.getClass}] in block: [$other]")
    }

    /** All classes used in this code block */
    def fqns: Seq[FullyQualifiedName] = code.flatMap {
      case name: FullyQualifiedName => Seq(name)
      case block: CodeBlock         => block.fqns
      case seq: Seq[_]              => new CodeBlock(seq).fqns
      case _                        => Seq.empty
    }
  }

  implicit class CodeBlockHelper(val sc: StringContext) extends AnyVal {
    def c(args: Any*): CodeBlock = {
      new CodeBlock(interleave(sc.parts.map(_.stripMargin), args))
    }

    private def interleave(strings: Seq[String], values: Seq[Any]): Seq[Any] =
      values.headOption match {
        case Some(value) => Seq(strings.head, value) ++ interleave(strings.tail, values.tail)
        case None =>
          require(strings.size == 1)
          strings
      }
  }
}
