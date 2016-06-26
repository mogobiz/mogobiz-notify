/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.actors

import java.util.UUID

import akka.actor.Actor
import com.mogobiz.notify.model.MogoNotify.{Notification, Device}
import com.mogobiz.notify.model.MogoNotify.Platform.Platform
import com.mogobiz.notify.config.MogonotifyHandlers._

object NotificationActor {

  case class Register(store: String,
                      deviceUuid: String,
                      regId: String,
                      clientId: Option[String],
                      platform: Platform,
                      lang: String)

  case class Unregister(store: String, regId: String)

  case class Notify(store: String, regIds: List[String], payload: String)

}

class NotificationActor extends Actor {

  import NotificationActor._

  def receive = {
    case Register(store, deviceUuid, regId, clientId, platform, lang) =>
      sender ! notificationHandler.register(
          Device(UUID.randomUUID().toString, store, deviceUuid, regId, platform, lang, clientId))

    case Unregister(store, regId) =>
      sender ! notificationHandler.unregister(store, regId)

    case Notify(store, regIds, payload) =>
      sender ! notificationHandler.notify(Notification[String](UUID.randomUUID().toString, store, regIds, "", payload))
  }

}
