package sse

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import sse.SseActor.SseCommand
import sse.SseMessage
/**
 * EventCoordinator is an actor that coordinate an event including the following:
 * - accept registration of attendees
 * - accept registration of speakers
 * - distribute event information to attendees
 * - receive notes from speakers
 */
object EventCoordinatorActor {
  sealed trait EventCoordinatorCommand
  case class EnlistSse(sse: ActorRef[SseCommand]) extends EventCoordinatorCommand
  case class RemoveSse(sse: ActorRef[SseCommand]) extends EventCoordinatorCommand
  case class EnlistSpeaker(name: String) extends EventCoordinatorCommand
  case class RemoveSpeaker(name: String) extends EventCoordinatorCommand
  case class PublishMessage(message: SseMessage) extends EventCoordinatorCommand

  def apply(event: String): Behavior[EventCoordinatorCommand] = Behaviors.setup(context => new EventCoordinator(context, event, List.empty))

  class EventCoordinator(context: ActorContext[EventCoordinatorCommand], val event: String, var sses: List[ActorRef[SseCommand]]) extends AbstractBehavior[EventCoordinatorCommand](context) {
    override def onMessage(msg: EventCoordinatorCommand): Behavior[EventCoordinatorCommand] = {
      msg match {
        case EnlistSse(sse) =>
          context.log.info(s"EventCoordinator received attendee: $sse")
          sses = sses :+ sse
          this
        case RemoveSse(sse) =>
          context.log.info(s"EventCoordinator received attendee removal: $sse")
          sses = sses.filter(_ != sse)
          this
        case EnlistSpeaker(name) =>
          context.log.info(s"EventCoordinator received speaker: $name")
          this
        case RemoveSpeaker(name) =>
          context.log.info(s"EventCoordinator received speaker removal: $name")
          this
        case PublishMessage(message) =>
          context.log.info(s"EventCoordinator received message: $message")
          sses.foreach(_ ! SseActor.PushServerSentEvent(message))
          this
      }
    }
  }


}