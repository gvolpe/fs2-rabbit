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

import com.itv.fs2rabbit.arguments.Arguments
import com.itv.fs2rabbit.model.{AckResult, AmqpEnvelope, BasicQos, QueueName}
import com.rabbitmq.client.Channel

trait AckerConsumer[F[_]] {

  def createAcker(channel: Channel): F[AckResult] => F[Unit]

  def createConsumer(queueName: QueueName,
                     channel: Channel,
                     basicQos: BasicQos,
                     autoAck: Boolean = false,
                     noLocal: Boolean = false,
                     exclusive: Boolean = false,
                     consumerTag: String = "",
                     args: Arguments = Map.empty): F[AmqpEnvelope]

}
