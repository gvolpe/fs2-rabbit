/*
 * Copyright 2017-2019 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.interpreter

import javax.net.ssl.SSLContext
import cats.effect.{Concurrent, ConcurrentEffect, Resource}
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig}
import com.github.gvolpe.fs2rabbit.config.deletion.{DeletionExchangeConfig, DeletionQueueConfig}
import com.github.gvolpe.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.program._
import fs2.Stream

// $COVERAGE-OFF$
object Fs2Rabbit {
  def apply[F[_]: ConcurrentEffect](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext] = None
  ): F[Fs2Rabbit[F]] =
    ConnectionBuilder.mkConnectionFactory[F](config, sslContext).map {
      case (factory, addresses) =>
        val amqpClient  = new AMQPClientStream[F]
        val connBuilder = new ConnectionBuilder[F](factory, addresses)
        val internalQ   = new LiveInternalQueue[F](config.internalQueueSize.getOrElse(500))
        val acker       = new AckingProgram[F](config, amqpClient)
        val consumer    = new ConsumingProgram[F](amqpClient, internalQ)
        new Fs2Rabbit[F](config, connBuilder, amqpClient, acker, consumer)
    }
}
// $COVERAGE-ON$

class Fs2Rabbit[F[_]: Concurrent] private[fs2rabbit] (
    config: Fs2RabbitConfig,
    connectionBuilder: Connection[F],
    amqpClient: AMQPClient[Stream[F, ?], F],
    acker: Acking[F],
    consumer: Consuming[Stream[F, ?], F]
) {

  private[fs2rabbit] val consumingProgram: AckConsuming[Stream[F, ?], F] =
    new AckConsumingProgram[F](acker, consumer)

  private[fs2rabbit] val publishingProgram: Publishing[Stream[F, ?], F] =
    new PublishingProgram[F](amqpClient)

  def createConnectionChannelResource: Resource[F, AMQPChannel] = connectionBuilder.createConnectionChannel

  def createConnectionChannel: Stream[F, AMQPChannel] = Stream.resource(connectionBuilder.createConnectionChannel)

  def createAckerConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel,
    decoder: EnvelopeDecoder[F, A]): Stream[F, (AckResult => F[Unit], Stream[F, AmqpEnvelope[A]])] =
    consumingProgram.createAckerConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createAutoAckConsumer[A](
      queueName: QueueName,
      basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
      consumerArgs: Option[ConsumerArgs] = None
  )(implicit channel: AMQPChannel, decoder: EnvelopeDecoder[F, A]): Stream[F, Stream[F, AmqpEnvelope[A]]] =
    consumingProgram.createAutoAckConsumer(channel.value, queueName, basicQos, consumerArgs)

  def createPublisher[A](exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]): Stream[F, A => F[Unit]] =
    publishingProgram.createPublisher(channel.value, exchangeName, routingKey)

  def createPublisherWithListener[A](
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): Stream[F, A => F[Unit]] =
    publishingProgram.createPublisherWithListener(channel.value, exchangeName, routingKey, flags, listener)

  def createRoutingPublisher[A](exchangeName: ExchangeName)(
      implicit channel: AMQPChannel,
      encoder: MessageEncoder[F, A]): Stream[F, RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisher(channel.value, exchangeName)

  def createRoutingPublisherWithListener[A](
      exchangeName: ExchangeName,
      flags: PublishingFlag,
      listener: PublishReturn => F[Unit]
  )(implicit channel: AMQPChannel, encoder: MessageEncoder[F, A]): Stream[F, RoutingKey => A => F[Unit]] =
    publishingProgram.createRoutingPublisherWithListener(channel.value, exchangeName, flags, listener)

  def addPublishingListener(listener: PublishReturn => F[Unit])(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.addPublishingListener(channel.value, listener)

  def clearPublishingListeners(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.clearPublishingListeners(channel.value)

  def basicCancel(consumerTag: ConsumerTag)(implicit channel: AMQPChannel): F[Unit] =
    amqpClient.basicCancel(channel.value, consumerTag)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey)

  def bindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindQueueNoWait(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindQueueNoWait(channel.value, queueName, exchangeName, routingKey, args)

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    unbindQueue(queueName, exchangeName, routingKey, QueueUnbindArgs(Map.empty))

  def unbindQueue(queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueUnbindArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.unbindQueue(channel.value, queueName, exchangeName, routingKey, args)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeBindingArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchange(channel.value, destination, source, routingKey, args)

  def bindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    bindExchange(destination, source, routingKey, ExchangeBindingArgs(Map.empty))

  def bindExchangeNoWait(
      destination: ExchangeName,
      source: ExchangeName,
      routingKey: RoutingKey,
      args: ExchangeBindingArgs
  )(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.bindExchangeNoWait(channel.value, destination, source, routingKey, args)

  def unbindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey, args: ExchangeUnbindArgs)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.unbindExchange(channel.value, destination, source, routingKey, args)

  def unbindExchange(destination: ExchangeName, source: ExchangeName, routingKey: RoutingKey)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    unbindExchange(destination, source, routingKey, ExchangeUnbindArgs(Map.empty))

  def declareExchange(exchangeName: ExchangeName, exchangeType: ExchangeType)(
      implicit channel: AMQPChannel): Stream[F, Unit] =
    declareExchange(DeclarationExchangeConfig.default(exchangeName, exchangeType))

  def declareExchange(exchangeConfig: DeclarationExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchange(channel.value, exchangeConfig)

  def declareExchangeNoWait(exchangeConfig: DeclarationExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchangeNoWait(channel.value, exchangeConfig)

  def declareExchangePassive(exchangeName: ExchangeName)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareExchangePassive(channel.value, exchangeName)

  def declareQueue(queueConfig: DeclarationQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueue(channel.value, queueConfig)

  def declareQueueNoWait(queueConfig: DeclarationQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueueNoWait(channel.value, queueConfig)

  def declareQueuePassive(queueName: QueueName)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.declareQueuePassive(channel.value, queueName)

  def deleteQueue(config: DeletionQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueue(channel.value, config)

  def deleteQueueNoWait(config: DeletionQueueConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteQueueNoWait(channel.value, config)

  def deleteExchange(config: DeletionExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteExchange(channel.value, config)

  def deleteExchangeNoWait(config: DeletionExchangeConfig)(implicit channel: AMQPChannel): Stream[F, Unit] =
    amqpClient.deleteExchangeNoWait(channel.value, config)

}
