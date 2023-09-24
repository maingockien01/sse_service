import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.{DelayOverflowStrategy, OverflowStrategy}
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import sse.{CoordinatorLeadActor, EventCoordinatorActor, SpeakerActor, SpeakerRoute, SseActor, SseRoute}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Service {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>

    implicit val system = ctx.system

    val coordinatorLead = ctx.spawn(CoordinatorLeadActor(), "coordinatorLead")
    ctx.system.receptionist ! Receptionist.Register(CoordinatorLeadActor.CoordinatorLeadServiceKey, coordinatorLead)

    val onlyOneEventNow = "event"

    val speakerActor = ctx.spawn(SpeakerActor(onlyOneEventNow), "speakerActor")
    speakerActor ! SpeakerActor.ConnectToEvent()



    val speakerRoute = new SpeakerRoute(speakerActor)

    val (sourceQueue, eventsSource) = Source.queue[String](Int.MaxValue, OverflowStrategy.backpressure)
      .delay(1.seconds, DelayOverflowStrategy.backpressure)
      .map(message => ServerSentEvent(message))
      .keepAlive(1.second, () => ServerSentEvent.heartbeat)
      .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
      .run()
    val sseActor = ctx.spawn(SseActor(onlyOneEventNow, sourceQueue), "sseActor")
    sseActor ! SseActor.SelfSubscribeEvent()

    val sseRoute = new SseRoute(eventsSource)


    val routes: Route = {
      Directives.concat(
        speakerRoute.theSpeakerRoutes,
        sseRoute.theSseRoute
      )
    }
    val serverBinding: Future[Http.ServerBinding] =
      Http().newServerAt(host, port).bind(routes)
    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          ctx.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          ctx.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          if (wasStopped) ctx.self ! Stop
          running(binding)
        case Stop =>
          // we got a stop message but haven't completed starting yet,
          // we cannot stop until starting has completed
          starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Service.Message] =
      ActorSystem(Service("localhost", 8080), "SSE_Service")
  }


}
