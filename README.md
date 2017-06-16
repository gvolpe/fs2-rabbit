fs2-rabbit
==========

[ ![Codeship Status for gvolpe/fs2-rabbit](https://app.codeship.com/projects/ae5afcb0-28c2-0135-02b9-5656a7048432/status?branch=master)](https://app.codeship.com/projects/223399)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/011c5931cd3945b3a88eb725f18bbf88)](https://www.codacy.com/app/volpegabriel/fs2-rabbit?utm_source=github.com&utm_medium=referral&utm_content=gvolpe/fs2-rabbit&utm_campaign=badger)
[![Coverage Status](https://coveralls.io/repos/github/gvolpe/fs2-rabbit/badge.svg?branch=master)](https://coveralls.io/github/gvolpe/fs2-rabbit?branch=master)

Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](https://github.com/functional-streams-for-scala/fs2) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

***Disclaimer:** It's still under development and it was created just to solve my specific cases, but more features will be added as needed. Contributors are welcomed :)*

## Dependencies

Add the only dependency to your build.sbt:

```scala
resolvers += Opts.resolver.sonatypeSnapshots

libraryDependencies += "com.github.gvolpe" %% "fs2-rabbit" % "0.0.9-SNAPSHOT"
```

fs2-rabbit depends on fs2 v0.10.0-M2, cats.effects v0.3, circe v0.8.0 and amqp-client v4.1.0.

## Usage

#### Configuration

By default, fs2-rabbit will look into the **application.conf** file for the configuration of the library, here's an example:

```scala
fs2-rabbit {
  connection {
    virtual-host = "/"
    host = "127.0.0.1"
    username = "guest"
    password = "guest"
    port = 5672
    ssl = false
    connection-timeout = 3
  }

  requeue-on-nack = false
}
```

See reference.conf for more.

#### Creating connection, channel, "acker-consumer" and publisher

Connection and Channel will be acquired in a safe way, so in case of an error, the resources will be cleaned up.

**F** represents the effect type. In the example Demo I use **cats.effect.IO** but it's also possible to use either **scalaz.Task** or **monix.eval.Task**.

```scala
import com.github.gvolpe.fs2rabbit.Fs2Rabbit._

implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

val program = for {
  connAndChannel    <- createConnectionChannel[F]()                // Stream[F, (Connection, Channel)]
  (_, channel)      = connAndChannel
  _                 <- declareQueue[F](channel, queueName)         // Stream[F, Queue.DeclareOk]
  (acker, consumer) = createAckerConsumer[F](channel, queueName)	// (StreamAcker[F], StreamConsumer[F])
  publisher         = createPublisher[F](channel, "", routingKey)	// StreamPublisher[F]
  _                 <- doSomething(consumer, acker, publisher)
} yield ()

// Only once in your program...
program.run.unsafeRunSync()

// StreamAcker is a type alias for Sink[F, AckResult]
// StreamConsumer is a type alias for Stream[F, AmqpEnvelope]
// StreamPublisher is a type alias for Sink[F, AmqpMessage[String]]

```

#### Message Consuming and Acknowledge

It is possible to create either an **autoAckConsumer** or an **ackerConsumer**. If we choose the first one then we only need to worry about consuming the message. If we choose the latter instead, then we are in control of acking / nacking back to RabbitMQ. Here's a simple example on how you can do it:

```scala
import com.github.gvolpe.fs2rabbit.model._
import fs2.Pipe

def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
  for {
    amqpMsg <- streamMsg
    _       <- Stream.eval(F.delay(println(s"Consumed: $amqpMsg")))
  } yield Ack(amqpMsg.deliveryTag)
}

consumer through logPipe to acker
```

#### Json message Consuming

A stream-based Json Decoder that can be connected to a StreamConsumer is provided out of the box. Implicit decoders for your classes must be on scope (you can use Circe's codec auto derivation):

```scala
import com.github.gvolpe.fs2rabbit.json.Fs2JsonDecoder._
import io.circe._
import io.circe.generic.auto._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

(consumer through jsonDecode[Person]) flatMap {
  case (Left(error), tag) => (Stream.eval(F.delay(error)) to errorSink).map(_ => Nack(tag)) to acker
  case (Right(msg), tag)  => Stream.eval(F.delay((msg, tag))) to processorSink
}
```

#### Publishing

To publish a simple String message is very simple:

```scala
import com.github.gvolpe.fs2rabbit.model._
import fs2._

val message = AmqpMessage("Hello world!", AmqpProperties.empty)

Stream(message).covary[F] to publisher
```

#### Publishing Json Messages

A stream-based Json Encoder that can be connected to a StreamPublisher is provided out of the box. Very similar to the Json Decoder shown above, but in this case, implicit encoders for your classes must be on scope (again you can use Circe's codec auto derivation):

```scala
import com.github.gvolpe.fs2rabbit.json.Fs2JsonEncoder._
import com.github.gvolpe.fs2rabbit.model._
import io.circe.generic.auto._
import fs2._

case class Address(number: Int, streetName: String)
case class Person(id: Long, name: String, address: Address)

val message = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

Stream(message).covary[F] through jsonEncode[F, Person] to publisher
```

#### Resiliency

If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time. An useful StreamLoop object that you can use to achieve this is provided by the library.

So, for the program defined above, this would be an example of a resilient app that restarts every 3 seconds in case of failure:

```scala
import com.github.gvolpe.fs2rabbit.StreamLoop
import scala.concurrent.duration._

implicit val appR = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")
implicit val appS = IOEffectScheduler // or MonixEffectScheduler if using Monix Task

StreamLoop.run(() => program, 3.seconds)
```

See the [examples](https://github.com/gvolpe/fs2-rabbit/tree/master/examples/src/main/scala/com/github/gvolpe/fs2rabbit/examples) to learn more!

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
