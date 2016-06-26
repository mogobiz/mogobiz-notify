/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.handlers

import javapns.devices.implementations.basic.BasicDevice
import javapns.notification._

import akka.util.Timeout
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.notify.config.Settings
import com.mogobiz.notify.model.MogoNotify.{Device, Notification}
import com.mogobiz.system.ActorSystemLocator
import com.sksamuel.elastic4s.ElasticDsl._
import spray.client.pipelining._
import spray.http._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationHandler {
  implicit val timeout: Timeout = 40.seconds
  implicit val system           = ActorSystemLocator()
  import system.dispatcher

  def register(device: Device): Boolean = {
    val req = search in Settings.Notification.EsIndex -> "Device" postFilter {
      and(
          termFilter("deviceUuid", device.deviceUuid),
          termFilter("storeCode", device.storeCode)
      )
    }
    // We delete the existing device if any && upsert
    EsClient.update(Settings.Notification.EsIndex, device, true, false)
  }

  def unregister(storeCode: String, regId: String): Boolean = {
    val req = search in Settings.Notification.EsIndex types "Device" postFilter {
      and(
          termFilter("regId", regId),
          termFilter("storeCode", storeCode)
      )
    }
    val devices = EsClient.search[Device](req)
    devices.foreach(d => EsClient.delete[Device](Settings.Notification.EsIndex, d.uuid, false))
    true
  }

  def notify[T](notification: Notification[T]): Future[HttpResponse] = {
    val (androids, ioss) = notification.regIds.partition(regId => Device.isAndroid(regId))
    gcmNotify(androids, notification.payload)
    apnsNotify(ioss, notification.payload)
  }

  @tailrec
  private def gcmNotify[T](regIds: List[String], data: T): Future[HttpResponse] = {
    case class GCMRequest(registration_ids: List[String], data: String)
    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.

    val pipeline: SendReceive = (
          addHeader("Content-Type", "application/json")
            ~> addCredentials(BasicHttpCredentials(s"key=${Settings.Notification.Gcm.ApiKey}"))
            ~> sendReceive
      )

    val MaxNotifs = 1000
    val toSendIds = if (regIds.length > MaxNotifs) regIds.take(MaxNotifs) else regIds
    val payload   = JacksonConverter.mapper.writeValueAsString(GCMRequest(toSendIds, data.toString))
    val res       = pipeline(Post("https://android.googleapis.com/gcm/send", payload))
    if (regIds.length > MaxNotifs)
      gcmNotify(regIds.drop(MaxNotifs), data)
    else
      res
  }

  lazy val appleNotificationServer: AppleNotificationServer = {
    new AppleNotificationServerBasicImpl(Settings.Notification.Apns.Keystore,
                                         Settings.Notification.Apns.Password,
                                         Settings.Notification.Apns.KeystoreType,
                                         Settings.Notification.Apns.Host,
                                         Integer.parseInt(Settings.Notification.Apns.Port))
  }

  @tailrec
  private def apnsNotify[T](regIds: List[String], data: T): Future[HttpResponse] = {
    case class APNSContent(badge: Integer, alert: String)
    // content-available :Integer

    case class APNSRequest(aps: APNSContent)

    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.

    val MaxNotifs = 1000
    val res = Future {
      val jsonData    = JacksonConverter.mapper.writeValueAsString(APNSRequest(APNSContent(1, data.toString)))
      val payload     = new PushNotificationPayload(jsonData)
      val pushManager = new PushNotificationManager()
      pushManager.initializeConnection(appleNotificationServer)

      val toSendIds     = if (regIds.length > MaxNotifs) regIds.take(MaxNotifs) else regIds
      val devices       = toSendIds.map(new BasicDevice(_))
      val notifications = pushManager.sendNotifications(payload, devices: _*)
      val successCount  = PushedNotification.findSuccessfulNotifications(notifications).size()
      if (successCount == 0)
        HttpResponse(StatusCodes.InternalServerError)
      else
        HttpResponse(StatusCodes.OK)
    }
    if (regIds.length > MaxNotifs)
      apnsNotify(regIds.drop(MaxNotifs), data)
    else
      res
  }
}
