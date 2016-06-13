/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.services

import akka.actor.ActorRef
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import com.mogobiz.json.Implicits._
import com.mogobiz.notify.actors.NotificationActor._
import com.mogobiz.notify.model.MogoNotify.Platform._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
/*
  case class Register(store: String, regId: String, clientId: String, platform: Platform, lang: String)

  case class Unregister(store: String, regId: String)

  case class Notify(regIds: List[String], payload: String)

 */
class NotificationService(notificationActor: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {
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
          import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
          (notificationActor ? register).mapTo[Boolean]
        }
      }
    }
  }

  lazy val unregister = path("unregister") {
    get {
      parameters('store, 'regId).as(Unregister) { unregister =>
        complete {
          import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
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
