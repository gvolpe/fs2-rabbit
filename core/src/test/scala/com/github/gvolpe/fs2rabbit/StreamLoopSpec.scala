package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, IO}
import fs2._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class StreamLoopSpec extends FlatSpecLike with Matchers {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val s  = fs2.Scheduler.fromFixedDaemonPool(2, "restarter")

  implicit val es = new EffectScheduler[IO] {
    override def schedule[A](body: IO[A], delay: FiniteDuration)
                            (implicit ec: ExecutionContext, s: Scheduler, F: Effect[IO]) = {
      IO.async[Unit] { cb => s.scheduleOnce(delay)(cb(Right(()))) }.flatMap(_ => body)
    }
    override def unsafeRunSync(effect: IO[Unit]) = effect.unsafeRunSync()
  }

  it should "run a stream until it's finished" in {
    val sink = Fs2Utils.liftSink[IO, Int](n => IO(println(n)))
    val program = Stream(1,2,3).covary[IO] to sink
    StreamLoop.run(() => program)
  }

  it should "run a stream and recover in case of failure" in {
    val sink: Sink[IO, Int] = streamN => {
      streamN.flatMap { n =>
        if (1 == n) Stream.fail(new Exception("on purpose"))
        else Stream.eval(IO(println(n)))
      }
    }
    val program = Stream.eval(IO(Random.nextInt(2))) to sink
    StreamLoop.run(() => program, 1.second)
  }

}
