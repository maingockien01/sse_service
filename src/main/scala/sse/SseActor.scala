package sse

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.Timeout
import sse.CoordinatorLeadActor.CoordinatorLeadCommand
import java.util.concurrent.TimeUnit
import scala.util.Success

object SseActor {
  sealed trait SseCommand

  case class PushServerSentEvent(message: SseMessage) extends SseCommand
  case class SubscribeEvent(eventCoordinatorActor: ActorRef[CoordinatorLeadCommand]) extends SseCommand
  case class SelfSubscribeEvent() extends SseCommand

  def apply(event: String, sourceQueue: SourceQueueWithComplete[String]): Behavior[SseCommand] = Behaviors.setup(context => {
    new SseActor(context, event, sourceQueue)
  })

  class SseActor(context: ActorContext[SseActor.SseCommand], event: String, sourceQueue: SourceQueueWithComplete[String]) extends AbstractBehavior[SseActor.SseCommand](context) {
    override def onMessage(msg: SseCommand): Behavior[SseCommand] = {
      msg match {
        case PushServerSentEvent(message) =>
          context.log.info(s"SseActor received message: $message")
          sourceQueue.offer(message.toString)
          this
        case SelfSubscribeEvent() =>
          implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

          context.log.info(s"SseActor received subscribe event")
          context.ask(
            context.system.receptionist,
            Receptionist.Find(CoordinatorLeadActor.CoordinatorLeadServiceKey)
          ) {
            case Success(listing) => {
              val instances = listing.serviceInstances(CoordinatorLeadActor.CoordinatorLeadServiceKey)
              val coordinatorLead = instances.head

              SubscribeEvent(coordinatorLead)
            }
            case _ => throw new RuntimeException("Unable to find coordinator lead")
          }
          this
        case SubscribeEvent(eventCoordinatorActor) =>
          context.log.info(s"SseActor received subscribe event")
          eventCoordinatorActor ! CoordinatorLeadActor.ConnectSseToEvent(context.self, event)
          this
        case _ => this
      }
    }
  }


}
