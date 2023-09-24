package sse

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class SpeakerRoute(speakerActor: ActorRef[SpeakerActor.SpeakerCommand])(implicit val system: ActorSystem[_]) extends SseMessageJson {

  lazy val theSpeakerRoutes: Route =
    pathPrefix("speaker") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[SseMessage]) { message =>
                system.log.info(s"Speaker received message: $message")
                speakerActor ! SpeakerActor.PublishMessage(message)
                complete("Message received")
              }
            }
          )
        }
      )
    }

}
