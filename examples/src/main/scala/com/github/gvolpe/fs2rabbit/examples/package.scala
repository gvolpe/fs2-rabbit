package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.examples.scheduler.{IOEffectScheduler, MonixEffectScheduler}

package object examples {

  implicit val iOEffectScheduler    = IOEffectScheduler
  implicit val monixEffectScheduler = MonixEffectScheduler

}
