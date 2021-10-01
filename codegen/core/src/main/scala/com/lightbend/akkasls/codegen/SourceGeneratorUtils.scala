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
  val managedComment = """/* This code is managed by Akka Serverless tooling.
                         | * It will be re-generated to reflect any changes to your protobuf definitions.
                         | * DO NOT EDIT
                         | */""".stripMargin

  val unmanagedComment = """|
                            | *
                            | * This class was initially generated based on the .proto definition by Akka Serverless tooling.
                            | *
                            | * As long as this file exists it will not be overwritten: you can maintain it yourself,
                            | * or delete it so it is regenerated as needed.""".stripMargin

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

  class Imports(val currentPackage: String, _imports: Seq[String], val semi: Boolean) {

    val imports: Seq[String] = _imports.filterNot(isInCurrentPackage)

    private def isInCurrentPackage(imp: String): Boolean = {
      val i = imp.lastIndexOf('.')
      if (i == -1)
        currentPackage == ""
      else
        currentPackage == imp.substring(0, i)
    }

    def contains(imp: String): Boolean = imports.contains(imp)

    override def toString: String = {
      val suffix = if (semi) ";" else ""
      imports
        .map(pkg => s"import $pkg$suffix")
        .mkString("\n")
    }

  }

  def generateImports(
      types: Iterable[FullyQualifiedName],
      packageName: String,
      otherImports: Seq[String],
      packageImports: Seq[String] = Seq.empty,
      semi: Boolean = true): Imports = {
    val messageTypeImports = types
      .filterNot { typ =>
        typ.parent.javaPackage == packageName
      }
      .filterNot { typ =>
        packageImports.contains(typ.parent.javaPackage)
      }
      .map(typeImport)

    new Imports(packageName, (messageTypeImports ++ otherImports ++ packageImports).toSeq.distinct.sorted, semi)
  }

  def generateCommandImports(
      commands: Iterable[Command],
      state: State,
      packageName: String,
      otherImports: Seq[String],
      semi: Boolean = true): Imports = {
    val types = commandTypes(commands) :+ state.fqn
    generateImports(types, packageName, otherImports, Seq.empty, semi)
  }

  def generateCommandAndTypeArgumentImports(
      commands: Iterable[Command],
      typeArguments: Iterable[TypeArgument],
      packageName: String,
      otherImports: Seq[String],
      semi: Boolean = true): Imports = {
    val types = commandTypes(commands) ++ typeArguments.collect { case MessageTypeArgument(fqn) =>
      fqn
    }
    generateImports(types, packageName, otherImports ++ extraTypeImports(typeArguments), Seq.empty, semi)
  }

  def extraTypeImports(typeArguments: Iterable[TypeArgument]): Seq[String] =
    typeArguments.collect { case ScalarTypeArgument(ScalarType.Bytes) =>
      "com.google.protobuf.ByteString"
    }.toSeq

  def commandTypes(commands: Iterable[Command]): Seq[FullyQualifiedName] =
    commands.flatMap(command => Seq(command.inputType, command.outputType)).toSeq

  def typeName(fqn: FullyQualifiedName)(implicit imports: Imports): String = {
    if (imports.contains(fqn.fullQualifiedName)) fqn.name
    else if (fqn.parent.javaPackage == imports.currentPackage) fqn.name
    else if (imports.contains(fqn.parent.javaPackage))
      fqn.parent.javaPackage.split("\\.").last + "." + fqn.name
    else fqn.fullQualifiedName
  }

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
}
