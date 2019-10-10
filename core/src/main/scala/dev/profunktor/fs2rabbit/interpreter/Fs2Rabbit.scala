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

package dev.profunktor.fs2rabbit.interpreter

import cats.Applicative
import cats.effect._
import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig}
import dev.profunktor.fs2rabbit.algebra._
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import dev.profunktor.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.model._
import dev.profunktor.fs2rabbit.program._
import fs2.Stream
import javax.net.ssl.SSLContext

object Fs2Rabbit {
  def create[F[_]: ConcurrentEffect: ContextShift](
      config: Fs2RabbitConfig,
      blocker: Blocker,
      sslContext: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): Fs2Rabbit[F] = {
    val conn              = ConnectionEffect[F](config, sslContext, saslConfig)
    val consumeClient     = ConsumeEffect[F]
    val publishClient     = PublishEffect[F](blocker)
    val bindingClient     = BindingEffect[F]
    val declarationClient = DeclarationEffect[F]
    val deletionClient    = DeletionEffect[F]

    val internalQ: InternalQueue[F]                     = new LiveInternalQueue[F](config.internalQueueSize.getOrElse(500))
    val consumingProgram: AckConsuming[F, Stream[F, ?]] = AckConsumingProgram[F](config, internalQ)
    val publishingProgram: Publishing[F]                = PublishingProgram[F](blocker)

    new Fs2Rabbit[F](
      conn,
      consumeClient,
      publishClient,
      bindingClient,
      declarationClient,
      deletionClient,
      consumingProgram,
      publishingProgram
    )
  }

  // This is for retrocompatibility
  def apply[F[_]: ConcurrentEffect: ContextShift](
      config: Fs2RabbitConfig,
      blocker: Blocker,
      sslContext: Option[SSLContext] = None,
      // Unlike SSLContext, SaslConfig is not optional because it is always set
      // by the underlying Java library, even if the user doesn't set it.
      saslConfig: SaslConfig = DefaultSaslConfig.PLAIN
  ): F[Fs2Rabbit[F]] = Applicative[F].pure(create(config, blocker, sslContext, saslConfig))

}

class Fs2Rabbit[F[_]: Concurrent] private[fs2rabbit] (
    connection: Connection[Resource[F, ?]],
    consume: Consume[F],
    publish: Publish[F],
    binding: Binding[F],
    declaration: Declaration[F],
    deletion: Deletion[F],
    consumingProgram: AckConsuming[F, Stream[F, ?]],
    publishingProgram: Publishing[F]
) {

  def createChannel(conn: AMQPConnection): Resource[F, AMQPChannel] =
    connection.createChannel(conn)

  def createConnection: Resource[F, AMQPConnection] =
    connection.createConnection

  def createConnectionChannel: Resource[F, AMQPChannel] =
    createConnection.flatMap(createChannel)

  def createAckerConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(
      implicit channel: AMQPChannel,
      decoder: EnvelopeDecoder[F, A]
  ): F[(AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] =
    consumingProgram.createAckerConsumer(
      channel.value,
      queueName,
      basicQos,
      consumerArgs
    )

  def createAutoAckConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): F[Stream[F, AmqpEnvelope[A]]] =
    consumingProgram.createAutoAckConsumer(
      channel.value,
      queueName,
      basicQos,
      consumerArgs
    )

  def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[A => F[Unit]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

  def createPublisherWithListener[A](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[A => F[Unit]] =
    publishingProgram.createPublisherWithListener(
      channel.value,
      exchangeName,
      routingKey,
      flags,
      listener
    )

  def createBasicPublisher[A](
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisher(channel.value)

  def createBasicPublisherWithListener[A](flag: PublishingFlag, listener: PublishReturn => F[Unit])(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[(ExchangeName, RoutingKey, A) => F[Unit]] =
    publishingProgram.createBasicPublisherWithListener(
      channel.value,
      flag,
      listener
    )

  def createRoutingPublisher[A](exchangeName: ExchangeName)(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]
  ): F[RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisher(channel.value, exchangeName)

  def createRoutingPublisherWithListener[A](
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): F[RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisherWithListener(
      channel.value,
      exchangeName,
      flags,
      listener
    )

  def addPublishingListener(
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel): F[Unit] =
    publish.addPublishingListener(channel.value, listener)

  def clearPublishingListeners(implicit channel: AMQPChannel): F[Unit] =
    publish.clearPublishingListeners(channel.value)

  def basicCancel(
      consumerTag: ConsumerTag
  )(implicit channel: AMQPChannel): F[Unit] =
    consume.basicCancel(channel.value, consumerTag)

  def bindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindQueueNoWait(
      channel.value,
      queueName,
      exchangeName,
      routingKey,
      args
    )

  def unbindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    unbindQueue(queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))

  def unbindQueue(
      queueName: QueueName,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      args: QueueUnbindArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.unbindQueue(
      channel.value,
      queueName,
      exchangeName,
      routingKey,
      args
    )

  def bindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindExchange(channel.value, destination, source, routingKey, args)

  def bindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    bindExchange(
      destination,
      source,
      routingKey,
      ExchangeBindingArgs(Map.empty)
    )

  def bindExchangeNoWait(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.bindExchangeNoWait(
      channel.value,
      destination,
      source,
      routingKey,
      args
    )

  def unbindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeUnbindArgs
  )(implicit channel: AMQPChannel): F[Unit] =
    binding.unbindExchange(channel.value, destination, source, routingKey, args)

  def unbindExchange(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey
  )(implicit channel: AMQPChannel): F[Unit] =
    unbindExchange(
      destination,
      source,
      routingKey,
      ExchangeUnbindArgs(Map.empty)
    )

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel
  ): F[Unit] =
    declareExchange(
      DeclarationExchangeConfig.default(exchangeName, exchangeType)
    )

  def declareExchange(
      exchangeConfig: DeclarationExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchange(channel.value, exchangeConfig)

  def declareExchangeNoWait(
      exchangeConfig: DeclarationExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchangeNoWait(channel.value, exchangeConfig)

  def declareExchangePassive(
      exchangeName: ExchangeName
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareExchangePassive(channel.value, exchangeName)

  def declareQueue(implicit channel: AMQPChannel): F[QueueName] =
    declaration.declareQueue(channel.value)

  def declareQueue(
      queueConfig: DeclarationQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueue(channel.value, queueConfig)

  def declareQueueNoWait(
      queueConfig: DeclarationQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueueNoWait(channel.value, queueConfig)

  def declareQueuePassive(
      queueName: QueueName
  )(implicit channel: AMQPChannel): F[Unit] =
    declaration.declareQueuePassive(channel.value, queueName)

  def deleteQueue(
      config: DeletionQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteQueue(channel.value, config)

  def deleteQueueNoWait(
      config: DeletionQueueConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteQueueNoWait(channel.value, config)

  def deleteExchange(
      config: DeletionExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteExchange(channel.value, config)

  def deleteExchangeNoWait(
      config: DeletionExchangeConfig
  )(implicit channel: AMQPChannel): F[Unit] =
    deletion.deleteExchangeNoWait(channel.value, config)

}
