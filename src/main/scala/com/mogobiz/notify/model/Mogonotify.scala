/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.model

import java.util.{ Calendar, Date }

import akka.http.scaladsl.unmarshalling.{ FromStringUnmarshaller, Unmarshaller }
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.mogobiz.notify.config.Settings

import scala.concurrent.Future

object MogoNotify {
  type Document = String

  object Platform extends Enumeration {
    type Platform = Value
    val ANDROID = Value("ANDROID")
    val IOS = Value("IOS")
  }

  import Platform._

  implicit def PlatformUnmarshaller: FromStringUnmarshaller[Platform] = Unmarshaller(ex ⇒ value ⇒ Future.successful(Platform.withName(value)))

  class PlatformRef extends TypeReference[Platform.type]

  import com.mogobiz.notify.model.MogoNotify.Platform._

  case class Device(uuid: String,
    deviceUuid: String,
    storeCode: String,
    regId: String,
    @JsonScalaEnumeration(classOf[PlatformRef]) platform: Platform,
    lang: String,
    clientId: Option[String] = None,
    var dateCreated: Date = Calendar.getInstance().getTime,
    var lastUpdated: Date = Calendar.getInstance().getTime)

  case class Notification[T](uuid: String,
    store: String,
    regIds: List[String],
    lang: String,
    payload: T,
    var dateCreated: Date = Calendar.getInstance().getTime,
    var lastUpdated: Date = Calendar.getInstance().getTime)

  object Device {
    def isAndroid(regId: String) = !isIOS(regId)

    def isIOS(regId: String) = regId.length == Settings.Notification.Apns.TokenSize
  }

}
