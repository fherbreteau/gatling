/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.http

import java.io.RandomAccessFile
import java.net.ServerSocket

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Using

import io.gatling.commons.util.DefaultClock
import io.gatling.core.{ CoreComponents, EmptySession }
import io.gatling.core.action.ActorDelegatingAction
import io.gatling.core.actor.ActorSpec
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.pause.Constant
import io.gatling.core.protocol.{ Protocol, ProtocolComponentsRegistries }
import io.gatling.core.session.Session
import io.gatling.core.stats.NoopStatsEngine
import io.gatling.core.structure.{ ScenarioBuilder, ScenarioContext }
import io.gatling.http.cache.DnsCacheSupport
import io.gatling.http.client.resolver.InetAddressNameResolver
import io.gatling.http.client.util.MimeTypes
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.netty.util.Transports

import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.cookie._
import org.scalatest.BeforeAndAfter

abstract class HttpSpec extends ActorSpec with BeforeAndAfter with EmptySession {
  type ChannelProcessor = ChannelHandlerContext => Unit
  type Handler = PartialFunction[FullHttpRequest, ChannelProcessor]

  private val clock = new DefaultClock
  protected val mockHttpPort: Int = Using(new ServerSocket(0))(_.getLocalPort).getOrElse(8072)
  private val eventLoopGroup = Transports.newEventLoopGroup(true, false, 1, "test")

  private def httpProtocol(implicit configuration: GatlingConfiguration): HttpProtocolBuilder =
    HttpProtocolBuilder(configuration).baseUrl(s"http://localhost:$mockHttpPort")

  protected def runWithHttpServer(requestHandler: Handler)(f: HttpServer => Unit): Unit = {
    val httpServer = new HttpServer(requestHandler, mockHttpPort)
    try {
      f(httpServer)
    } finally {
      httpServer.stop()
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def runScenario(
      sb: ScenarioBuilder,
      timeout: FiniteDuration = 10.seconds,
      protocolCustomizer: HttpProtocolBuilder => HttpProtocolBuilder = identity
  )(implicit configuration: GatlingConfiguration): Session = {
    val protocols = Protocol.indexByType(Seq(protocolCustomizer(httpProtocol)))
    val coreComponents =
      new CoreComponents(actorSystem, eventLoopGroup, null, None, NoopStatsEngine, clock, null, configuration)
    val protocolComponentsRegistry = new ProtocolComponentsRegistries(coreComponents, protocols).scenarioRegistry(Map.empty)
    val nextActor = mockActorRef[Session]("next")
    val next = new ActorDelegatingAction("next", nextActor)
    val action = sb.build(new ScenarioContext(coreComponents, protocolComponentsRegistry, Constant, throttled = false), next)
    action ! emptySession
      .copy(eventLoop = eventLoopGroup.next())
      .set(DnsCacheSupport.DnsNameResolverAttributeName, InetAddressNameResolver.JAVA_RESOLVER)
    nextActor.expectMsgType[Session](timeout)
  }

  def sendFile(name: String): ChannelProcessor = ctx => {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    val resource = getClass.getClassLoader.getResource(name)
    val fileUri = resource.getFile
    val raf = new RandomAccessFile(fileUri, "r")
    val region = new DefaultFileRegion(raf.getChannel, 0, raf.length) // THIS WORKS ONLY WITH HTTP, NOT HTTPS

    response.headers
      .set(HttpHeaderNames.CONTENT_TYPE, MimeTypes.getMimeType(name))
      .set(HttpHeaderNames.CONTENT_LENGTH, raf.length)

    ctx.write(response)
    ctx.write(region)
    ctx
      .writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      .addListener(ChannelFutureListener.CLOSE)
  }

  // Assertions

  def verifyRequestTo(path: String)(implicit server: HttpServer): Unit = verifyRequestTo(path, 1)

  def verifyRequestTo(path: String, count: Int, checks: (FullHttpRequest => Unit)*)(implicit server: HttpServer): Unit = {
    val filteredRequests = server.requests.asScala.filter(_.uri == path).toList
    val actualCount = filteredRequests.size
    if (actualCount != count) {
      throw new AssertionError(s"Expected to access $path $count times, but actually accessed it $actualCount times.")
    }

    checks.foreach(check => filteredRequests.foreach(check))
  }

  def checkCookie(cookie: String, value: String)(request: FullHttpRequest): Unit = {
    val cookies = ServerCookieDecoder.STRICT.decode(request.headers.get(HttpHeaderNames.COOKIE)).asScala.toList
    val matchingCookies = cookies.filter(_.name == cookie)

    matchingCookies match {
      case Nil =>
        throw new AssertionError(s"In request $request there were no cookies")
      case list =>
        for (cookie <- list if cookie.value != value) {
          throw new AssertionError(s"$request: cookie '${cookie.name}', expected: '$value' but was '${cookie.value}'")
        }
    }
  }

  // Extractor for nicer interaction with Scala
  class HttpRequest(val request: FullHttpRequest) {
    def isEmpty: Boolean = request == null
    def get: (HttpMethod, String) = (request.method, request.uri)
  }

  object HttpRequest {
    def unapply(request: FullHttpRequest): HttpRequest = new HttpRequest(request)
  }

  override protected def afterAll(): Unit = {
    eventLoopGroup.shutdownGracefully()
    super.afterAll()
  }
}
