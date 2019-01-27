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

package com.github.gvolpe.fs2rabbit.examples

import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfig
import com.github.gvolpe.fs2rabbit.interpreter.Fs2Rabbit
import com.github.gvolpe.fs2rabbit.resiliency.ResilientStream

import scalaz.zio.{App, Clock, IO}
import scalaz.zio.interop.Task
import scalaz.zio.interop.catz._

object ZIOAutoAckConsumer extends App {

  implicit val clock = Clock.Live
  implicit val timer = ioTimer[Throwable]

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(
    virtualHost = "/",
    host = "127.0.0.1",
    username = Some("guest"),
    password = Some("guest"),
    port = 5672,
    ssl = false,
    connectionTimeout = 3,
    requeueOnNack = false,
    internalQueueSize = Some(500)
  )

  override def run(args: List[String]): IO[Nothing, ExitStatus] =
    Fs2Rabbit[Task](config)
      .flatMap { implicit fs2Rabbit =>
        ResilientStream
          .run(new AutoAckConsumerDemo[Task].program)
      }
      .run
      .map(_ => ExitStatus.ExitNow(1))

}
