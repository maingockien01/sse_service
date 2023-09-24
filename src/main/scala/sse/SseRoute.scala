package sse

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, Materializer, OverflowStrategy}
import sse.SseActor.SseCommand

import scala.concurrent.duration._

class SseRoute (eventsSource: Source[ServerSentEvent, NotUsed]) extends SseMessageJson {
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  lazy val theSseRoute: Route = {

    pathPrefix("sse") {
      concat(
        pathEnd {
          concat(
            get {
              complete {
                eventsSource
              }
            }
          )
        }
      )
    }

  }
}
