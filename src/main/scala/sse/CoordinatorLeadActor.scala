package sse

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import sse.EventCoordinatorActor.EventCoordinatorCommand
import sse.SseActor.SseCommand

object CoordinatorLeadActor {

  val CoordinatorLeadServiceKey: ServiceKey[CoordinatorLeadCommand] = ServiceKey[CoordinatorLeadCommand]("CoordinatorLeadService")
  sealed trait CoordinatorLeadCommand

  case class CreateEvent(event: String) extends CoordinatorLeadCommand

  case class ConnectSseToEvent(sse: ActorRef[SseCommand], event: String) extends CoordinatorLeadCommand

  case class ConnectSpeakerToEvent(speaker: ActorRef[SpeakerActor.SpeakerCommand], event: String) extends CoordinatorLeadCommand


  def apply(): Behavior[CoordinatorLeadCommand] = Behaviors.setup(context => {

    val coordinatorLead = new CoordinatorLead(context, Map.empty)

    context.system.receptionist ! Receptionist.Register(CoordinatorLeadServiceKey, context.self)

    coordinatorLead

  })

  class CoordinatorLead(context: ActorContext[CoordinatorLeadCommand], var eventsToCoordinators: Map[String, ActorRef[EventCoordinatorCommand]]) extends AbstractBehavior[CoordinatorLeadCommand](context) {
    override def onMessage(msg: CoordinatorLeadCommand): Behavior[CoordinatorLeadCommand] = {
      msg match {
        case CreateEvent(event) =>
          eventCoordinator(event)
          this
        case ConnectSseToEvent(sse, event) =>
          eventCoordinator(event) ! EventCoordinatorActor.EnlistSse(sse)
          this
        case ConnectSpeakerToEvent(speaker, event) =>
          speaker ! SpeakerActor.ConnectToEventCoordinator(eventCoordinator(event))
          this
        case _ => this
      }
    }

    private def eventCoordinator(event: String): ActorRef[EventCoordinatorCommand] = {
      if (!eventsToCoordinators.contains(event)) {
        val eventCoordinator = context.spawn(EventCoordinatorActor(event), event)
        eventsToCoordinators = eventsToCoordinators + (event -> eventCoordinator)
      }
      eventsToCoordinators(event)
    }
  }
}
