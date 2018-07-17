/*
 * Copyright 2017 Fs2 Rabbit
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

package com.itv.fs2rabbit.algebra

import com.itv.fs2rabbit.model.{AmqpMessage, ExchangeName, RoutingKey}
import com.itv.fs2rabbit.model.{AmqpMessage, ExchangeName, RoutingKey}
import com.rabbitmq.client.Channel

trait Publishing[F[_]] {
  def createPublisher(channel: Channel,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey): F[F[AmqpMessage[String]] => F[Unit]]
}
