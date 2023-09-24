package sse

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import sse.EventCoordinatorActor.EventCoordinatorCommand

import scala.util.Success;

object SpeakerActor {
  sealed trait SpeakerCommand
  //TODO: refactor to be able to specify which event that the message should be published to
  final case class PublishMessage(message: SseMessage) extends SpeakerCommand
  final case class ConnectToEvent() extends SpeakerCommand
  final case class ConnectToCoordinatorLead(coordinatorLeadActor: ActorRef[CoordinatorLeadActor.CoordinatorLeadCommand], event: String) extends SpeakerCommand
  final case class ConnectToEventCoordinator(eventCoordinatorActor: ActorRef[EventCoordinatorCommand]) extends SpeakerCommand

  def apply(event: String): Behavior[SpeakerCommand] = Behaviors.setup(context => new Speaker(context, event))

  class Speaker(context: ActorContext[SpeakerCommand], event: String) extends AbstractBehavior[SpeakerCommand](context) {
    var eventCoordinator: Option[ActorRef[EventCoordinatorCommand]] = None
    override def onMessage(msg: SpeakerCommand): Behavior[SpeakerCommand] = {
      msg match {
        case PublishMessage(message) =>
          context.log.info(s"Speaker received message: $message")
          eventCoordinator match {
            case Some(eventCoordinatorActor) => eventCoordinatorActor ! EventCoordinatorActor.PublishMessage(message)
            case None => context.log.info(s"Speaker received message but no event coordinator")
          }
          this
        case ConnectToEvent() =>
          implicit val timeout: Timeout = Timeout.apply(100, java.util.concurrent.TimeUnit.MILLISECONDS)
          context.log.info(s"Speaker received connect to event")
          context.ask(
            context.system.receptionist,
            Receptionist.Find(CoordinatorLeadActor.CoordinatorLeadServiceKey)
          ) {
            case Success(listing) => {
              val instances = listing.serviceInstances(CoordinatorLeadActor.CoordinatorLeadServiceKey)
              val coordinatorLead = instances.head

              ConnectToCoordinatorLead(coordinatorLead, event)
            }
            case _ => throw new RuntimeException("Unable to find coordinator lead")
          }
          this

        case ConnectToCoordinatorLead(coordinatorLeadActor, event) =>
          context.log.info(s"Speaker received connect to coordinator lead")
          coordinatorLeadActor ! CoordinatorLeadActor.ConnectSpeakerToEvent(context.self, event)
          this

        case ConnectToEventCoordinator(eventCoordinatorActor) =>
          context.log.info(s"Speaker received connect to event coordinator")
          eventCoordinator = Some(eventCoordinatorActor)
          this
      }
    }
  }

}
