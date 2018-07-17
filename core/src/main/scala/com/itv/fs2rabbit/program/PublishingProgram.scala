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

package com.itv.fs2rabbit.program

import cats.effect.Sync
import com.itv.fs2rabbit.algebra.{AMQPClient, Publishing}
import com.itv.fs2rabbit.model.{ExchangeName, RoutingKey, StreamPublisher}
import com.itv.fs2rabbit.algebra.Publishing
import com.itv.fs2rabbit.util.StreamEval
import com.rabbitmq.client.Channel
import fs2.Stream

class PublishingProgram[F[_]: Sync](AMQP: AMQPClient[Stream[F, ?], F])(implicit SE: StreamEval[F])
    extends Publishing[Stream[F, ?]] {

  override def createPublisher(channel: Channel,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey): Stream[F, StreamPublisher[F]] =
    SE.evalF {
      _.flatMap { msg =>
        AMQP.basicPublish(channel, exchangeName, routingKey, msg)
      }
    }

}
