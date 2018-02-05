/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.handlers

import javapns.devices.implementations.basic.BasicDevice
import javapns.notification._

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.util.Timeout
import com.mogobiz.es.EsClient
import com.mogobiz.json.JacksonConverter
import com.mogobiz.notify.config.Settings
import com.mogobiz.notify.model.MogoNotify.{Device, Notification}
import com.mogobiz.system.ActorSystemLocator
import com.mogobiz.utils.HttpRequestor
import com.sksamuel.elastic4s.http.ElasticDsl._

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class NotificationHandler {
  implicit val timeout: Timeout = 40.seconds
  implicit val system = ActorSystemLocator()
  import system.dispatcher

  def register(device: Device): Boolean = {
    val req = search(Settings.Notification.EsIndex -> "Device") query {
      boolQuery().must(termQuery("deviceUuid", device.deviceUuid),
                       termQuery("storeCode", device.storeCode))
    }
    // We delete the existing device if any && upsert
    EsClient.update(Settings.Notification.EsIndex, device, true, false)
  }

  def unregister(storeCode: String, regId: String): Boolean = {
    val req = search(Settings.Notification.EsIndex -> "Device") query {
      boolQuery().must(termQuery("regId", regId),
                       termQuery("storeCode", storeCode))
    }
    val devices = EsClient.search[Device](req)
    devices.foreach(d =>
      EsClient.delete[Device](Settings.Notification.EsIndex, d.uuid, false))
    true
  }

  def notify[T](notification: Notification[T]): Future[HttpResponse] = {
    val (androids, ioss) =
      notification.regIds.partition(regId => Device.isAndroid(regId))
    gcmNotify(androids, notification.payload)
    apnsNotify(ioss, notification.payload)
  }

  @tailrec
  private def gcmNotify[T](regIds: List[String],
                           data: T): Future[HttpResponse] = {

    case class GCMRequest(registration_ids: List[String], data: String)

    val MaxNotifs = 1000
    val toSendIds =
      if (regIds.lengthCompare(MaxNotifs) > 0) regIds.take(MaxNotifs)
      else regIds
    val payload =
      JacksonConverter.serialize(GCMRequest(toSendIds, data.toString))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://android.googleapis.com/gcm/send"),
      entity = HttpEntity(MediaTypes.`application/json`, payload),
      headers = List(
        Authorization(
          BasicHttpCredentials("key", Settings.Notification.Gcm.ApiKey))
      )
    )

    val res = HttpRequestor.doRequest(request)
    if (regIds.lengthCompare(MaxNotifs) > 0)
      gcmNotify(regIds.drop(MaxNotifs), data)
    else
      res
    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.
  }

  lazy val appleNotificationServer: AppleNotificationServer = {
    new AppleNotificationServerBasicImpl(
      Settings.Notification.Apns.Keystore,
      Settings.Notification.Apns.Password,
      Settings.Notification.Apns.KeystoreType,
      Settings.Notification.Apns.Host,
      Integer.parseInt(Settings.Notification.Apns.Port)
    )
  }

  @tailrec
  private def apnsNotify[T](regIds: List[String],
                            data: T): Future[HttpResponse] = {

    case class APNSContent(badge: Integer, alert: String)

    // content-available :Integer

    case class APNSRequest(aps: APNSContent)

    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.

    val MaxNotifs = 1000
    val res = Future {
      val jsonData =
        JacksonConverter.serialize(APNSRequest(APNSContent(1, data.toString)))
      val payload = new PushNotificationPayload(jsonData)
      val pushManager = new PushNotificationManager()
      pushManager.initializeConnection(appleNotificationServer)

      val toSendIds =
        if (regIds.lengthCompare(MaxNotifs) > 0) regIds.take(MaxNotifs)
        else regIds
      val devices = toSendIds.map(new BasicDevice(_))
      val notifications = pushManager.sendNotifications(payload, devices: _*)
      val successCount =
        PushedNotification.findSuccessfulNotifications(notifications).size()
      if (successCount == 0)
        HttpResponse(StatusCodes.InternalServerError)
      else
        HttpResponse(StatusCodes.OK)
    }
    if (regIds.lengthCompare(MaxNotifs) > 0)
      apnsNotify(regIds.drop(MaxNotifs), data)
    else
      res
  }
}
