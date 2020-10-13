/*
 * Copyright 2017-2020 ProfunKtor
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

package dev.profunktor.fs2rabbit.algebra

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.functor._
import dev.profunktor.fs2rabbit.arguments._
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.effects.BoolValue.syntax._
import dev.profunktor.fs2rabbit.model.{AMQPChannel, ExchangeName, QueueName}

object Declaration {
  def make[F[_]: Sync: ContextShift](blocker: Blocker): Declaration[F] = new Declaration[F] {
    override def declareExchange(channel: AMQPChannel, config: DeclarationExchangeConfig): F[Unit] =
      blocker.delay {
        channel.value.exchangeDeclare(
          config.exchangeName.value,
          config.exchangeType.toString.toLowerCase,
          config.durable.isTrue,
          config.autoDelete.isTrue,
          config.internal.isTrue,
          config.arguments
        )
      }.void

    override def declareExchangeNoWait(
        channel: AMQPChannel,
        config: DeclarationExchangeConfig
    ): F[Unit] =
      blocker.delay {
        channel.value.exchangeDeclareNoWait(
          config.exchangeName.value,
          config.exchangeType.toString.toLowerCase,
          config.durable.isTrue,
          config.autoDelete.isTrue,
          config.internal.isTrue,
          config.arguments
        )
      }.void

    override def declareExchangePassive(channel: AMQPChannel, exchangeName: ExchangeName): F[Unit] =
      blocker.delay {
        channel.value.exchangeDeclarePassive(exchangeName.value)
      }.void

    override def declareQueue(channel: AMQPChannel): F[QueueName] =
      blocker.delay {
        QueueName(channel.value.queueDeclare().getQueue)
      }

    override def declareQueue(channel: AMQPChannel, config: DeclarationQueueConfig): F[Unit] =
      blocker.delay {
        channel.value.queueDeclare(
          config.queueName.value,
          config.durable.isTrue,
          config.exclusive.isTrue,
          config.autoDelete.isTrue,
          config.arguments
        )
      }.void

    override def declareQueueNoWait(channel: AMQPChannel, config: DeclarationQueueConfig): F[Unit] =
      blocker.delay {
        channel.value.queueDeclareNoWait(
          config.queueName.value,
          config.durable.isTrue,
          config.exclusive.isTrue,
          config.autoDelete.isTrue,
          config.arguments
        )
      }.void

    override def declareQueuePassive(channel: AMQPChannel, queueName: QueueName): F[Unit] =
      blocker.delay {
        channel.value.queueDeclarePassive(queueName.value)
      }.void
  }
}

trait Declaration[F[_]] {
  def declareExchange(channel: AMQPChannel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangeNoWait(value: AMQPChannel, exchangeConfig: DeclarationExchangeConfig): F[Unit]
  def declareExchangePassive(channel: AMQPChannel, exchangeName: ExchangeName): F[Unit]
  def declareQueue(channel: AMQPChannel): F[QueueName]
  def declareQueue(channel: AMQPChannel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueueNoWait(channel: AMQPChannel, queueConfig: DeclarationQueueConfig): F[Unit]
  def declareQueuePassive(channel: AMQPChannel, queueName: QueueName): F[Unit]
}
