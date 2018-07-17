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

package com.itv.fs2rabbit.json

import cats.effect.Sync
import com.itv.fs2rabbit.model.AmqpMessage
import com.itv.fs2rabbit.util.StreamEval
import fs2.Pipe
import io.circe.Encoder
import io.circe.syntax._

/**
  * Stream-based Json Encoder that exposes only one method as a streaming transformation
  * using [[fs2.Pipe]] and depends on the Circe library.
  * */
class Fs2JsonEncoder[F[_]](implicit SE: StreamEval[F]) {

  /**
    * It tries to encode a given case class encapsulated in an  [[AmqpMessage]] into a
    * json string.
    *
    * For example:
    *
    * {{{
    * import fs2._
    *
    * val payload = Person("Sherlock", Address(212, "Baker St"))
    * val p = Stream(AmqpMessage(payload, AmqpProperties.empty)).covary[IO] through jsonEncode[IO, Person]
    *
    * p.run.unsafeRunSync
    * }}}
    *
    * The result will be an [[AmqpMessage]] of type [[String]]
    * */
  def jsonEncode[A: Encoder]: Pipe[F, AmqpMessage[A], AmqpMessage[String]] =
    streamMsg =>
      for {
        amqpMsg <- streamMsg
        json    <- SE.evalF[String](amqpMsg.payload.asJson.noSpaces)
      } yield AmqpMessage(json, amqpMsg.properties)

}
