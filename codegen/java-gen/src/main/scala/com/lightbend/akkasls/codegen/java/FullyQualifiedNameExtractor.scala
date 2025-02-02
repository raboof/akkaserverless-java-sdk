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

package com.lightbend.akkasls.codegen.java

import com.google.protobuf.Descriptors
import com.lightbend.akkasls.codegen.{ FullyQualifiedName, ModelBuilder, PackageNaming }

object FullyQualifiedNameExtractor extends ModelBuilder.FullyQualifiedNameExtractor {
  override def apply(descriptor: Descriptors.GenericDescriptor): FullyQualifiedName =
    FullyQualifiedName(descriptor.getName, descriptor.getName, packageName(descriptor))

  override def packageName(descriptor: Descriptors.GenericDescriptor): PackageNaming = {
    val fileDescriptor = descriptor.getFile
    if (fileDescriptor.getName == s"google.protobuf.${descriptor.getName}.placeholder.proto") {
      // In the case of placeholders for standard google types, we need to assume the package naming
      // These defaults are based on the protos from https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf
      PackageNaming(
        descriptor.getName,
        descriptor.getName,
        fileDescriptor.getPackage,
        Some(s"com.${fileDescriptor.getPackage}"),
        Some(s"${descriptor.getName}Proto"),
        javaMultipleFiles = true)
    } else PackageNaming.from(fileDescriptor)
  }

  override def fileDescriptorObject(descriptor: Descriptors.FileDescriptor): FullyQualifiedName = {
    val parent = apply(descriptor).parent
    FullyQualifiedName(parent.javaOuterClassname, parent)
  }
}
