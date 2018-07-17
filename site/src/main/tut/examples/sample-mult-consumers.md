---
layout: docs
title:  "Multiple Consumers"
number: 16
---

# Multiple Consumers

Given two `Consumers` bound to queues with different `RoutingKey`s `RKA` and `RKB` and a single `Publisher` bound to a single `RoutingKey` named `RKA` we will be publishing messages to both queues but expecting to only consume messages published to the `RKA`. The second consumer bound to `RKB` will not receive any messages:

```tut:book:silent
import cats.effect.IO
import com.itv.fs2rabbit.config.declaration.DeclarationQueueConfig
import com.itv.fs2rabbit.interpreter.Fs2Rabbit
import com.itv.fs2rabbit.model._
import fs2._

import scala.concurrent.ExecutionContext.Implicits.global

val q1  = QueueName("q1")
val q2  = QueueName("q2")
val ex  = ExchangeName("testEX")
val rka = RoutingKey("RKA")
val rkb = RoutingKey("RKB")

val msg = Stream(AmqpMessage("Hey!", AmqpProperties.empty)).covary[IO]

def multipleConsumers(c1: StreamConsumer[IO], c2: StreamConsumer[IO], p: StreamPublisher[IO]) = {
  Stream(
    msg to p,
    c1 to (_.evalMap(m => IO(println(s"Consumer #1 >> $m")))),
    c2 to (_.evalMap(m => IO(println(s"Consumer #2 >> $m"))))
  ).join(3)
}

def program(F: Fs2Rabbit[IO]) = F.createConnectionChannel.flatMap { implicit channel =>
    for {
      _  <- F.declareExchange(ex, ExchangeType.Topic)
      _  <- F.declareQueue(DeclarationQueueConfig.default(q1))
      _  <- F.declareQueue(DeclarationQueueConfig.default(q2))
      _  <- F.bindQueue(q1, ex, rka)
      _  <- F.bindQueue(q2, ex, rkb)
      c1 <- F.createAutoAckConsumer(q1)
      c2 <- F.createAutoAckConsumer(q2)
      p  <- F.createPublisher(ex, rka)
      _  <- multipleConsumers(c1, c2, p)
    } yield ()
  }
```

If we run this program, we should only see a message `Consumer #1 >> Hey!` meaning that only the consumer bound to the `RKA` routing key got the message.
