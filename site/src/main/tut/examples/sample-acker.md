---
layout: docs
title:  "Single AckerConsumer"
number: 16
---

# Single AckerConsumer

Here we create a single `AckerConsumer`, a single `Publisher` and finally we publish two messages: a simple `String` message and a `Json` message by using the `fs2-rabbit-circe` extension.

```tut:book:silent
import cats.effect.Concurrent
import com.itv.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import com.itv.fs2rabbit.json.Fs2JsonEncoder
import com.itv.fs2rabbit.model.AckResult.Ack
import com.itv.fs2rabbit.model.AmqpHeaderVal.{LongVal, StringVal}
import com.itv.fs2rabbit.model._
import com.itv.fs2rabbit.util.StreamEval
import fs2.{Pipe, Stream}

import scala.concurrent.ExecutionContext

class Flow[F[_]: Concurrent](consumer: StreamConsumer[F],
                             acker: StreamAcker[F],
                             logger: Pipe[F, AmqpEnvelope, AckResult],
                             publisher: StreamPublisher[F])(implicit ec: ExecutionContext, SE: StreamEval[F]) {

  import io.circe.generic.auto._

  case class Address(number: Int, streetName: String)
  case class Person(id: Long, name: String, address: Address)

  private val jsonEncoder = new Fs2JsonEncoder[F]
  import jsonEncoder.jsonEncode

  val simpleMessage =
    AmqpMessage(
        "Hey!",
        AmqpProperties(None, None, None, None, Map("demoId" -> LongVal(123), "app" -> StringVal("fs2RabbitDemo"))))
  val classMessage = AmqpMessage(Person(1L, "Sherlock", Address(212, "Baker St")), AmqpProperties.empty)

  val flow: Stream[F, Unit] =
    Stream(
      Stream(simpleMessage).covary[F] to publisher,
      Stream(classMessage).covary[F] through jsonEncode[Person] to publisher,
      consumer through logger to acker
    ).join(3)

}

class AckerConsumerDemo[F[_]: Concurrent](implicit F: Fs2Rabbit[F], EC: ExecutionContext, SE: StreamEval[F]) {

  private val queueName    = QueueName("testQ")
  private val exchangeName = ExchangeName("testEX")
  private val routingKey   = RoutingKey("testRK")

  def logPipe: Pipe[F, AmqpEnvelope, AckResult] = { streamMsg =>
    for {
      amqpMsg <- streamMsg
      _       <- SE.evalF[Unit](println(s"Consumed: $amqpMsg"))
    } yield Ack(amqpMsg.deliveryTag)
  }

  val program: Stream[F, Unit] = F.createConnectionChannel flatMap { implicit channel =>
    for {
      _                 <- F.declareQueue(DeclarationQueueConfig.default(queueName))
      _                 <- F.declareExchange(exchangeName, ExchangeType.Topic)
      _                 <- F.bindQueue(queueName, exchangeName, routingKey)
      ackerConsumer     <- F.createAckerConsumer(queueName)
      (acker, consumer) = ackerConsumer
      publisher         <- F.createPublisher(exchangeName, routingKey)
      result            <- new Flow(consumer, acker, logPipe, publisher).flow
    } yield result
  }

}
```

At the edge of out program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:

```tut:book:silent
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import com.itv.fs2rabbit.config.Fs2RabbitConfig
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import com.itv.fs2rabbit.resiliency.ResilientStream

object IOAckerConsumer extends IOApp {

  implicit val appS: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val config: Fs2RabbitConfig = Fs2RabbitConfig(virtualHost = "/",
                                                        host = "127.0.0.1",
                                                        username = Some("guest"),
                                                        password = Some("guest"),
                                                        port = 5672,
                                                        ssl = false,
                                                        sslContext = None,
                                                        connectionTimeout = 3,
                                                        requeueOnNack = false)

  override def run(args: List[String]): IO[ExitCode] =
    Fs2Rabbit[IO](config).flatMap { implicit interpreter =>
      ResilientStream.run(new AckerConsumerDemo[IO]().program)
    }.as(ExitCode.Success)
}
```
