/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.services

import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.util.Timeout
import com.mogobiz.notify.config.MogonotifyHandlers.notificationHandler
import com.mogobiz.notify.model.MogoNotify.{Device, Notification}
import com.mogobiz.utils.HttpComplete
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class Register(store: String,
                    deviceUuid: String,
                    regId: String,
                    clientId: Option[String],
                    platform: String,
                    lang: String)

case class Unregister(store: String, regId: String)

case class Notify(store: String, regIds: List[String], payload: String)

class NotificationService(notificationActor: ActorRef)
    extends Directives
    with HttpComplete {
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
      parameters('store,
                 'deviceUuid,
                 'regId,
                 'clientId.?,
                 'platform.as[String],
                 'lang).as(Register) { register: Register =>
        val device = Device(UUID.randomUUID().toString,
                            register.store,
                            register.deviceUuid,
                            register.regId,
                            register.platform,
                            register.lang,
                            register.clientId)

        handleCall(notificationHandler.register(device),
                   (res: Boolean) => complete(StatusCodes.OK -> res))
      }
    }
  }

  lazy val unregister = path("unregister") {
    get {
      parameters('store, 'regId) { (store, regId) =>
        handleCall(notificationHandler.unregister(store, regId),
                   (res: Boolean) => complete(StatusCodes.OK -> res))
      }
    }
  }

  lazy val notification = path("notify") {
    get {
      parameterMultiMap { params =>
        val notification =
          Notify(params("store")(0), params("regId"), params("payload")(0))
        handleCall(
          notificationHandler.notify(
            Notification[String](UUID.randomUUID().toString,
                                 notification.store,
                                 notification.regIds,
                                 "",
                                 notification.payload)),
          (res: Future[HttpResponse]) =>
            complete(StatusCodes.OK -> res.map(_.status))
        )
      }
    }
  }
}
