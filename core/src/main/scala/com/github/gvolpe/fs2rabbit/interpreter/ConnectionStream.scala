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

import cats.effect.concurrent.MVar
import cats.effect.syntax.effect._
import cats.effect.{ConcurrentEffect, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.gvolpe.fs2rabbit.algebra.Connection
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.model.{AMQPChannel, RabbitChannel}
import com.github.gvolpe.fs2rabbit.effects.Log
import com.rabbitmq.client.{Connection => RabbitMQConnection, _}
import fs2.Stream

class ConnectionStream[F[_]: ConcurrentEffect](factory: ConnectionFactory)(implicit F: Sync[F], L: Log[F])
    extends Connection[Stream[F, ?]] {

  private[fs2rabbit] def acquireConnection(q: MVar[F, ShutdownSignalException]): F[(RabbitMQConnection, AMQPChannel)] =
    for {
      _       <- F.delay(factory.setAutomaticRecoveryEnabled(false))
      conn    <- F.delay(factory.newConnection)
      channel <- F.delay(conn.createChannel)
      _       = conn.addShutdownListener(shutdownListener(q))
    } yield (conn, RabbitChannel(channel))

  /**
    * Creates a connection and a channel in a safe way using Stream.bracket.
    * In case of failure, the resources will be cleaned up properly.
    **/
  override def createConnectionChannel: Stream[F, AMQPChannel] = {
    def makeChannel(queue: MVar[F, ShutdownSignalException]): Stream[F, AMQPChannel] =
      Stream
        .bracket(acquireConnection(queue)) {
          case (conn, RabbitChannel(channel)) =>
            L.info(s"Releasing connection: $conn previously acquired.") *>
              F.delay { if (channel.isOpen) channel.close() } *> F.delay { if (conn.isOpen) conn.close() }
          case (_, _) => F.raiseError[Unit](new Exception("Unreachable"))
        }
        .map { case (_, channel) => channel }

    def terminator(queue: MVar[F, ShutdownSignalException]): Stream[F, AMQPChannel] =
      Stream.eval(queue.read >>= F.raiseError[AMQPChannel])

    for {
      queue   <- Stream.eval(MVar[F].empty[ShutdownSignalException])
      channel <- makeChannel(queue).concurrently(terminator(queue))
    } yield channel
  }

  private def shutdownListener(queue: MVar[F, ShutdownSignalException]): ShutdownListener = cause => {
    queue.put(cause).toIO.unsafeRunAsync(_ => ())
  }

}

object ConnectionStream {

  private[fs2rabbit] def mkConnectionFactory[F[_]: Sync](
      config: Fs2RabbitConfig,
      sslContext: Option[SSLContext]
  ): F[ConnectionFactory] =
    Sync[F].delay {
      val factory = new ConnectionFactory()
      factory.setHost(config.host)
      factory.setPort(config.port)
      factory.setVirtualHost(config.virtualHost)
      factory.setConnectionTimeout(config.connectionTimeout)
      if (config.ssl) {
        sslContext.fold(factory.useSslProtocol())(factory.useSslProtocol)
      }
      config.username.foreach(factory.setUsername)
      config.password.foreach(factory.setPassword)
      factory
    }

}
