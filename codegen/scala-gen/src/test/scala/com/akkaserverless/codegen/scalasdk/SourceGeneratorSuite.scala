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

package com.akkaserverless.codegen.scalasdk

import com.lightbend.akkasls.codegen.TestData

class SourceGeneratorSuite extends munit.FunSuite {
  import com.akkaserverless.codegen.scalasdk.impl.SourceGenerator._
  import com.lightbend.akkasls.codegen.SourceGeneratorUtils._

  private val testData = TestData()

  test("it can generate a simple 'main'") {
    val main = generateMain(testData.simple())
    assertNoDiff(main.name, "com/example/Main.scala")
    assertNoDiff(
      main.content,
      s"""package com.example
         |
         |object Main extends App {
         |  println("Hello, world!")
         |}
         |""".stripMargin)
  }
}
