package sse

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}

trait SseMessageJson extends  SprayJsonSupport {
  import spray.json.DefaultJsonProtocol._
  import sse.SseMessage

  implicit object SseMessageJsonFormat extends RootJsonFormat[SseMessage] {
    def write(message: SseMessage): JsValue = JsObject(
      "message" -> JsString(message.message)
    )
    def read(value: JsValue): SseMessage = {
      value.asJsObject.getFields("message") match {
        case Seq(JsString(message)) => SseMessage(message)
        case _ => throw DeserializationException("SseMessage expected")
      }
    }
  }

}
