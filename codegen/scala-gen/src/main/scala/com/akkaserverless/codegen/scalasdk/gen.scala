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

import protocbridge.{ Artifact, SandboxedJvmGenerator }
import scalapb.GeneratorOption

object gen {
  def apply(
      options: Seq[String] = Seq.empty,
      generatorClass: String = "com.akkaserverless.codegen.scalasdk.AkkaserverlessGenerator$")
      : (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          com.akkaserverless.codegen.scalasdk.BuildInfo.organization,
          "akkaserverless-codegen-scala_2.12",
          com.akkaserverless.codegen.scalasdk.BuildInfo.version),
        generatorClass,
        AkkaserverlessGenerator.suggestedDependencies),
      options)

}
