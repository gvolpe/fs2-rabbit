/*
 * Copyright 2017-2019 ProfunKtor
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

package dev.profunktor.fs2rabbit.program

import cats.effect.{Bracket, Effect, Sync}
import cats.implicits._
import dev.profunktor.fs2rabbit.algebra.ConsumingStream._
import dev.profunktor.fs2rabbit.algebra.{AMQPInternals, Consume, InternalQueue}
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.EnvelopeDecoder
import dev.profunktor.fs2rabbit.interpreter.ConsumeEffect
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

object ConsumingProgram {
  def make[F[_]: Effect](internalQueue: InternalQueue[F]): F[ConsumingProgram[F]] = Sync[F].delay {
    WrapperConsumingProgram(internalQueue, Consume.make)
  }
}

trait ConsumingProgram[F[_]] extends ConsumingStream[F] with Consume[F]

case class WrapperConsumingProgram[F[_]: Effect] private (
    internalQueue: InternalQueue[F],
    consume: Consume[F]
) extends ConsumingProgram[F] {

  override def createConsumer[A](
      queueName: QueueName,
      channel: AMQPChannel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] = {

    val setup = for {
      internalQ <- internalQueue.create
      internals = AMQPInternals[F](Some(internalQ))
      _         <- consume.basicQos(channel, basicQos)
      consumerTag <- consume.basicConsume(
                      channel,
                      queueName,
                      autoAck,
                      consumerTag,
                      noLocal,
                      exclusive,
                      args
                    )(internals)
    } yield (consumerTag, internalQ)

    Stream
      .bracket(setup) {
        case (tag, _) =>
          consume.basicCancel(channel, tag)
      }
      .flatMap {
        case (_, queue) =>
          Stream.repeatEval(
            queue.dequeue1.rethrow
              .flatMap(env => decoder(env).map(a => env.copy(payload = a)))
          )
      }
      .pure[F]
  }

  override def basicAck(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean): F[Unit] =
    consume.basicAck(channel, tag, multiple)

  override def basicNack(channel: AMQPChannel, tag: DeliveryTag, multiple: Boolean, requeue: Boolean): F[Unit] =
    consume.basicNack(channel, tag, multiple, requeue)

  override def basicQos(channel: AMQPChannel, basicQos: BasicQos): F[Unit] =
    consume.basicQos(channel, basicQos)

  override def basicConsume[A](channel: AMQPChannel,
                               queueName: QueueName,
                               autoAck: Boolean,
                               consumerTag: ConsumerTag,
                               noLocal: Boolean,
                               exclusive: Boolean,
                               args: Arguments)(internals: AMQPInternals[F]): F[ConsumerTag] =
    consume.basicConsume(channel, queueName, autoAck, consumerTag, noLocal, exclusive, args)(internals)

  override def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): F[Unit] =
    consume.basicCancel(channel, consumerTag)
}

object ConsumingProgramOld {
  def apply[F[_]: Effect](
      internalQueue: InternalQueue[F]
  ): ConsumingProgramOld[F] =
    new ConsumingProgramOld[F] with ConsumeEffect[F] {
      override lazy val effect: Effect[F]              = Effect[F]
      override lazy val bracket: Bracket[F, Throwable] = effect
      override lazy val IQ: InternalQueue[F]           = internalQueue
    }
}

trait ConsumingProgramOld[F[_]] extends ConsumingStream[F] { this: Consume[F] =>

  val IQ: InternalQueue[F]
  implicit val bracket: Bracket[F, Throwable]

  override def createConsumer[A](
      queueName: QueueName,
      channel: AMQPChannel,
      basicQos: BasicQos,
      autoAck: Boolean = false,
      noLocal: Boolean = false,
      exclusive: Boolean = false,
      consumerTag: ConsumerTag = ConsumerTag(""),
      args: Arguments = Map.empty
  )(implicit decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] = {

    val setup = for {
      internalQ <- IQ.create
      internals = AMQPInternals[F](Some(internalQ))
      _         <- this.basicQos(channel, basicQos)
      consumerTag <- this.basicConsume(
                      channel,
                      queueName,
                      autoAck,
                      consumerTag,
                      noLocal,
                      exclusive,
                      args
                    )(internals)
    } yield (consumerTag, internalQ)

    Stream
      .bracket(setup) {
        case (tag, _) =>
          this.basicCancel(channel, tag)
      }
      .flatMap {
        case (_, queue) =>
          Stream.repeatEval(
            queue.dequeue1.rethrow
              .flatMap(env => decoder(env).map(a => env.copy(payload = a)))
          )
      }
      .pure[F]
  }
}
