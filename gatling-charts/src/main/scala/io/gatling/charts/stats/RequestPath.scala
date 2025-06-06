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

package io.gatling.charts.stats

private[charts] object RequestPath {
  private val Separator = " / "
  def path(group: Group): String = group.hierarchy.mkString(Separator)
  @SuppressWarnings(Array("org.wartremover.warts.ListAppend"))
  def path(requestName: String, group: Option[Group]): String = (group.map(_.hierarchy).getOrElse(Nil) :+ requestName).mkString(Separator)
}
