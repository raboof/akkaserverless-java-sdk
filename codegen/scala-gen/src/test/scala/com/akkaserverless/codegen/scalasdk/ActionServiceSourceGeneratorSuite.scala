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

import com.akkaserverless.codegen.scalasdk.impl.ActionServiceSourceGenerator
import com.akkaserverless.codegen.scalasdk.impl.ViewServiceSourceGenerator
import com.lightbend.akkasls.codegen.TestData

class ActionServiceSourceGeneratorSuite extends munit.FunSuite {

  private val testData = TestData()

  test("action source") {
    val service = testData.simpleActionService()
    val generatedSrc =
      ActionServiceSourceGenerator.actionSource(service)
    assertNoDiff(
      generatedSrc,
      """|package com.example.service
         |
         |import akka.NotUsed
         |import akka.stream.scaladsl.Source
         |import com.akkaserverless.scalasdk.action.Action
         |import com.akkaserverless.scalasdk.action.ActionCreationContext
         |import com.external.Empty
         |
         |/** An action. */
         |class MyServiceAction(creationContext: ActionCreationContext) extends AbstractMyServiceAction {
         |
         |  /** Handler for "SimpleMethod". */
         |  override def simpleMethod(myRequest: MyRequest): Action.Effect[Empty] = {
         |    throw new RuntimeException("The command handler for `SimpleMethod` is not implemented, yet")
         |  }
         |
         |  /** Handler for "StreamedOutputMethod". */
         |  override def streamedOutputMethod(myRequest: MyRequest): Source[Action.Effect[Empty], NotUsed] = {
         |    throw new RuntimeException("The command handler for `StreamedOutputMethod` is not implemented, yet")
         |  }
         |
         |  /** Handler for "StreamedInputMethod". */
         |  override def streamedInputMethod(myRequestSrc: Source[MyRequest, NotUsed]): Action.Effect[Empty] = {
         |    throw new RuntimeException("The command handler for `StreamedInputMethod` is not implemented, yet")
         |  }
         |
         |  /** Handler for "FullStreamedMethod". */
         |  override def fullStreamedMethod(myRequestSrc: Source[MyRequest, NotUsed]): Source[Action.Effect[Empty], NotUsed] = {
         |    throw new RuntimeException("The command handler for `FullStreamedMethod` is not implemented, yet")
         |  }
         |}
         |""".stripMargin)
  }

  test("abstract source") {
    val service = testData.simpleActionService()

    val generatedSrc =
      ActionServiceSourceGenerator.abstractAction(service)
    assertNoDiff(
      generatedSrc,
      """|package com.example.service
         |
         |import akka.NotUsed
         |import akka.stream.scaladsl.Source
         |import com.akkaserverless.scalasdk.action.Action
         |import com.external.Empty
         |
         |/** An action. */
         |abstract class AbstractMyServiceAction extends Action {
         |
         |  /** Handler for "SimpleMethod". */
         |  def simpleMethod(myRequest: MyRequest): Action.Effect[Empty]
         |
         |  /** Handler for "StreamedOutputMethod". */
         |  def streamedOutputMethod(myRequest: MyRequest): Source[Action.Effect[Empty], NotUsed]
         |
         |  /** Handler for "StreamedInputMethod". */
         |  def streamedInputMethod(myRequestSrc: Source[MyRequest, NotUsed]): Action.Effect[Empty]
         |
         |  /** Handler for "FullStreamedMethod". */
         |  def fullStreamedMethod(myRequestSrc: Source[MyRequest, NotUsed]): Source[Action.Effect[Empty], NotUsed]
         |}
         |""".stripMargin)
  }

  test("handler source") {
    val service = testData.simpleActionService()

    val generatedSrc =
      ActionServiceSourceGenerator.actionHandler(service)

    assertNoDiff(
      generatedSrc,
      """|package com.example.service
         |
         |import akka.NotUsed
         |import akka.stream.scaladsl.Source
         |import com.akkaserverless.javasdk.impl.action.ActionHandler.HandlerNotFound
         |import com.akkaserverless.scalasdk.action.Action
         |import com.akkaserverless.scalasdk.action.MessageEnvelope
         |import com.akkaserverless.scalasdk.impl.action.ActionHandler
         |import com.external.Empty
         |
         |/** A Action handler */
         |class MyServiceActionHandler(actionBehavior: MyServiceAction) extends ActionHandler[MyServiceAction](actionBehavior) {
         |
         |  override def handleUnary(commandName: String, message: MessageEnvelope[Any]):  Action.Effect[_] = {
         |    commandName match {
         |      case "SimpleMethod" =>
         |        action.simpleMethod(message.payload.asInstanceOf[MyRequest])
         |      case _ =>
         |        throw new HandlerNotFound(commandName)
         |    }
         |  }
         |
         |  override def handleStreamedOut(commandName: String, message: MessageEnvelope[Any]): Source[Action.Effect[_], NotUsed] = {
         |    commandName match {
         |      case "StreamedOutputMethod" =>
         |        action.streamedOutputMethod(message.payload.asInstanceOf[MyRequest])
         |      case _ =>
         |        throw new HandlerNotFound(commandName)
         |    }
         |  }
         |
         |  override def handleStreamedIn(commandName: String, stream: Source[MessageEnvelope[Any], NotUsed]): Action.Effect[_] = {
         |    commandName match {
         |      case "StreamedInputMethod" =>
         |        action.streamedInputMethod(stream.map(el => el.payload.asInstanceOf[MyRequest]))
         |      case _ =>
         |        throw new HandlerNotFound(commandName)
         |    }
         |  }
         |
         |  override def handleStreamed(commandName: String, stream: Source[MessageEnvelope[Any], NotUsed]): Source[Action.Effect[_], NotUsed] = {
         |    commandName match {
         |      case "FullStreamedMethod" =>
         |        action.fullStreamedMethod(stream.map(el => el.payload.asInstanceOf[MyRequest]))
         |      case _ =>
         |        throw new HandlerNotFound(commandName)
         |    }
         |  }
         |}
         |""".stripMargin)
  }

  test("provider source") {
    val service = testData.simpleActionService()

    val generatedSrc =
      ActionServiceSourceGenerator.actionProvider(service)

    assertNoDiff(
      generatedSrc,
      """|package com.example.service
         |
         |import com.akkaserverless.scalasdk.action.ActionCreationContext
         |import com.akkaserverless.scalasdk.action.ActionOptions
         |import com.akkaserverless.scalasdk.action.ActionProvider
         |import com.external.Empty
         |import com.google.protobuf.Descriptors
         |import scala.collection.immutable
         |
         |object MyServiceActionProvider {
         |  def apply(actionFactory: ActionCreationContext => MyServiceAction): MyServiceActionProvider =
         |    new MyServiceActionProvider(actionFactory, ActionOptions.defaults)
         |
         |  def apply(actionFactory: ActionCreationContext => MyServiceAction, options: ActionOptions): MyServiceActionProvider =
         |    new MyServiceActionProvider(actionFactory, options)
         |}
         |
         |class MyServiceActionProvider private(actionFactory: ActionCreationContext => MyServiceAction,
         |                                      options: ActionOptions)
         |  extends ActionProvider[MyServiceAction] {
         |
         |  override final def serviceDescriptor: Descriptors.ServiceDescriptor =
         |    MyServiceProto.javaDescriptor.findServiceByName("MyService")
         |
         |  override final def newHandler(context: ActionCreationContext): MyServiceActionHandler =
         |    new MyServiceActionHandler(actionFactory(context))
         |
         |  override final def additionalDescriptors: immutable.Seq[Descriptors.FileDescriptor] =
         |    MyServiceProto.javaDescriptor ::
         |    Nil
         |
         |  def withOptions(options: ActionOptions): MyServiceActionProvider =
         |    new MyServiceActionProvider(actionFactory, options)
         |}
         |""".stripMargin)
  }
}
