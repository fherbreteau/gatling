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

package io.gatling.http.engine

import java.{ util => ju }

import io.gatling.commons.util.DefaultClock
import io.gatling.core.config.GatlingConfiguration
import io.gatling.http.cache.HttpCaches
import io.gatling.http.client.Request
import io.gatling.http.client.uri.Uri
import io.gatling.http.engine.tx.{ HttpTx, ResourceTx }
import io.gatling.http.protocol.{ HttpComponents, HttpProtocol }
import io.gatling.http.request.{ HttpRequest, HttpRequestConfig }
import io.gatling.internal.quicklens._

import io.netty.handler.codec.http.DefaultHttpHeaders
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class HttpTxSpec extends AnyFlatSpecLike with Matchers {
  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  trait Context {
    val httpProtocol = HttpProtocol(configuration)
    val httpComponents = new HttpComponents(httpProtocol, null, new HttpCaches(new DefaultClock, configuration), null)

    val configBase = HttpRequestConfig(
      checks = Nil,
      responseTransformer = None,
      throttled = false,
      silent = None,
      followRedirect = false,
      checksumAlgorithms = Nil,
      storeBodyParts = false,
      defaultCharset = configuration.core.charset,
      explicitResources = Nil,
      httpProtocol = httpProtocol
    )
  }

  private def tx(clientRequest: Request, requestConfig: HttpRequestConfig, root: Boolean) =
    HttpTx(
      null,
      request = HttpRequest(
        requestName = "mockHttpTx",
        clientRequest = clientRequest,
        requestConfig = requestConfig
      ),
      next = null,
      resourceTx = if (root) None else Some(ResourceTx(null, "resources", null)),
      redirectCount = 0
    )

  private def request(uri: String): Request =
    new Request(
      null,
      null,
      Uri.create(uri),
      new DefaultHttpHeaders,
      ju.Collections.emptyList(),
      null,
      0L,
      false,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      false,
      null,
      null
    )

  "HttpTx" should "be silent when using default protocol and containing a request forced to silent" in new Context {
    val ahcRequest = request("http://example.com/")

    val config = configBase.copy(silent = Some(true))
    tx(ahcRequest, config, root = true).silent shouldBe true
  }

  it should "be non-silent when using default protocol and containing a regular request" in new Context {
    val ahcRequest = request("http://example.com/")

    tx(ahcRequest, configBase, root = true).silent shouldBe false
  }

  it should "not be silent when using a protocol with a silentUri pattern match the request url" in new Context {
    val ahcRequest = request("http://example.com/test.js")

    val config = configBase
      .modify(_.httpProtocol.requestPart)(_.modify(_.silentUri).setTo(Some(""".*\.js""".r.pattern)).modify(_.silentResources).setTo(false))

    tx(ahcRequest, config, root = true).silent shouldBe true
  }

  it should "be silent when passed a protocol silencing resources and a resource (non root) request" in new Context {
    val ahcRequest = request("http://example.com/test.js")

    val config = configBase
      .modify(_.httpProtocol.requestPart)(_.modify(_.silentUri).setTo(None).modify(_.silentResources).setTo(true))

    tx(ahcRequest, config, root = false).silent shouldBe true
  }

  it should "not be silent when passed a protocol silencing resources and a root request" in new Context {
    val ahcRequest = request("http://example.com/test.js")

    val config = configBase
      .modify(_.httpProtocol.requestPart)(_.modify(_.silentUri).setTo(None).modify(_.silentResources).setTo(true))

    tx(ahcRequest, config, root = true).silent shouldBe false
  }
}
