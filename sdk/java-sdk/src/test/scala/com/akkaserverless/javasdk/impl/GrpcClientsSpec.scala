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

package com.akkaserverless.javasdk.impl

import akka.Done
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.javadsl.AkkaGrpcClient
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletionStage
import scala.concurrent.Promise
import scala.jdk.FutureConverters.FutureOps

// dummy instead of depending on actual generated Akka gRPC client to keep it simple
trait PretendService {}
object PretendServiceClient {
  def create(settings: GrpcClientSettings): PretendServiceClient = new PretendServiceClient(settings)
}
class PretendServiceClient(val settings: GrpcClientSettings) extends PretendService with AkkaGrpcClient {
  private val closePromise = Promise[Done]()
  def close(): CompletionStage[Done] = {
    closePromise.success(Done)
    closed()
  }

  def closed(): CompletionStage[Done] = closePromise.future.asJava
}

class GrpcClientsSpec extends AnyWordSpec with Matchers with ScalaFutures {

  "The GrpcClients extension" must {
    "create the client for a service and pool it" in {
      val system = ActorSystem(
        "GrpcClientsSpec",
        ConfigFactory.parseString("""
          |akka.grpc.client.c {
          |  service-discovery {
          |    service-name = "my-service"
          |  }
          |  host = "my-host"
          |  port = 42
          |  override-authority = "google.fr"
          |  deadline = 10m
          |  user-agent = "Akka-gRPC"
          |}
          |""".stripMargin))

      try {
        val client1ForA = GrpcClients(system).getGrpcClient(classOf[PretendService], "a")
        client1ForA shouldBe a[PretendServiceClient]
        // no entry in config, so should be project inter-service call
        client1ForA match {
          case client: PretendServiceClient =>
            client.settings.serviceName should ===("a")
            client.settings.defaultPort should ===(80)
            client.settings.useTls should ===(false)
        }

        val client2ForA = GrpcClients(system).getGrpcClient(classOf[PretendService], "a")
        client2ForA shouldBe theSameInstanceAs(client1ForA)

        // same service protocol but different service
        val client1ForB = GrpcClients(system).getGrpcClient(classOf[PretendService], "b")
        (client1ForB shouldNot be).theSameInstanceAs(client1ForA)

        // same service protocol external service
        val client1ForC = GrpcClients(system).getGrpcClient(classOf[PretendService], "c")
        (client1ForC shouldNot be).theSameInstanceAs(client1ForA)
        client1ForC match {
          case client: PretendServiceClient =>
            client.settings.serviceName should ===("my-host")
            client.settings.defaultPort should ===(42)
            client.settings.useTls should ===(true)
        }

      } finally {
        TestKit.shutdownActorSystem(system)
      }

    }
  }

}
