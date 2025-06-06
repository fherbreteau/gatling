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

package io.gatling.charts.template

import io.gatling.charts.component.Stats

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class ConsoleTemplateSpec extends AnyFlatSpecLike with Matchers {
  "console template" should "format the request counters properly" in {
    val numberOfRequestsStatistics = new Stats("numberOfRequestsStatistics", Some(20L), Some(19L), Some(1L))
    val out = ConsoleTemplate.writeRequestCounters(numberOfRequestsStatistics)
    out shouldBe "> numberOfRequestsStatistics                                                         |        20 |        19 |         1"
  }

  it should "format the grouped counts properly" in {
    val out = ConsoleTemplate.writeRange("t < 42 ms", 90, 42)
    out shouldBe "> t < 42 ms                                                                                                  90    (42%)"
  }
}
