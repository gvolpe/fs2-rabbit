fs2-rabbit
==========

[![CircleCI](https://circleci.com/gh/profunktor/fs2-rabbit.svg?style=svg)](https://circleci.com/gh/profunktor/fs2-rabbit)
[![Gitter Chat](https://badges.gitter.im/profunktor-dev/fs2-rabbit.svg)](https://gitter.im/profunktor-dev/fs2-rabbit)
[![Maven Central](https://img.shields.io/maven-central/v/dev.profunktor/fs2-rabbit_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-rabbit) <a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>
[![MergifyStatus](https://img.shields.io/endpoint.svg?url=https://gh.mergify.io/badges/profunktor/fs2-rabbit&style=flat)](https://mergify.io)


Stream-based library for [RabbitMQ](https://www.rabbitmq.com/) built-in on top of [Fs2](http://fs2.io/) and the [RabbitMq Java Client](https://github.com/rabbitmq/rabbitmq-java-client).

## Dependencies

Add this to your build.sbt:

```scala
libraryDependencies += "dev.profunktor" %% "fs2-rabbit" % Version
```

And this one if you would like to have Json support:

```scala
libraryDependencies += "dev.profunktor" %% "fs2-rabbit-circe" % Version
```

Note: previous artifacts `<= 2.0.0-RC1` were published using the `com.github.gvolpe` group id (see [migration
guide](https://github.com/profunktor/fs2-rabbit/wiki/Migration-guide-(Vim)))

## Usage Guide

Check the [official guide](https://fs2-rabbit.profunktor.dev/guide.html) for updated compiling examples.

## Adopters

| Company | Description |
| ------- | ----------- |
| [Cognotekt](http://www.cognotekt.com/en) | Microservice workflow management in Insuretech AI applications. |
| [ITV](https://www.itv.com/) | Internal microservices interaction. |
| [Klarna](https://www.klarna.com/us/) | Microservice for Fintech services. |
| [Philips Lighting](http://www.lighting.philips.com/main/home) | Internal microservices interaction. |
| [Free2Move](https://free2move.com) | Microservice communication. |

## Running tests locally

Start a `RabbitMQ` instance using `docker` (recommended):

```bash
> docker run -p 5672:5672 rabbitmq:alpine
> sbt +test
```

## Code of Conduct

See the [Code of Conduct](https://fs2-rabbit.profunktor.dev/CODE_OF_CONDUCT)

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
