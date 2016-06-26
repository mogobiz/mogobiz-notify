/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.services

import akka.actor.ActorRef
import akka.util.Timeout
import com.mogobiz.notify.actors.NotificationActor._

import com.mogobiz.notify.model.MogoNotify.Platform._

import org.json4s.DefaultFormats
import spray.http.HttpResponse
import spray.routing.Directives
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext}
import akka.pattern.ask

import spray.httpx.Json4sJacksonSupport

object Implicits extends Json4sJacksonSupport {
  implicit val json4sJacksonFormats = DefaultFormats
}

import Implicits._
/*
  case class Register(store: String, regId: String, clientId: String, platform: Platform, lang: String)

  case class Unregister(store: String, regId: String)

  case class Notify(regIds: List[String], payload: String)

 */
class NotificationService(notificationActor: ActorRef)(implicit executionContext: ExecutionContext)
    extends Directives {
  implicit val timeout = Timeout(10.seconds)

  val route = get {
    pathPrefix("oauth") {
      pathPrefix("github") {
        register ~
        unregister ~
        notification
      }
    }
  }

  lazy val register = path("register") {
    get {
      parameters('store, 'deviceUuid, 'regId, 'clientId.?, 'platform.as[Platform], 'lang).as(Register) { register =>
        complete {
          (notificationActor ? register).mapTo[Boolean]
        }
      }
    }
  }

  lazy val unregister = path("unregister") {
    get {
      parameters('store, 'regId).as(Unregister) { unregister =>
        complete {
          (notificationActor ? unregister).mapTo[Boolean]
        }
      }
    }
  }

  lazy val notification = path("notify") {
    get {
      parameterMultiMap { params =>
        val notification = Notify(params("store")(0), params("regId"), params("payload")(0))
        complete {
          val response = (notificationActor ? notification).mapTo[HttpResponse]
          response.map(_.status)
        }
      }
    }
  }

}
