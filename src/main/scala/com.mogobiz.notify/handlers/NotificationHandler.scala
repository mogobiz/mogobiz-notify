package com.mogobiz.notify.handlers

import javapns.devices.implementations.basic.BasicDevice
import javapns.notification._

import com.mogobiz.json.JacksonConverter
import com.mogobiz.notify.Settings

import scala.annotation.tailrec
import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticDsl._
import com.mogobiz.notify.model.MogoNotify.{Device, Notification}
import com.mogobiz.es.EsClient
import spray.client.pipelining._
import spray.http._

import scala.concurrent.{Future}

class NotificationHandler {
  def register(device: Device): Boolean = {
    val req = search in Settings.Notification.EsIndex -> "Device" filter {
      and(
        termFilter("deviceUuid", device.deviceUuid),
        termFilter("storeCode", device.storeCode)
      )
    }
    // We delete the existing device if any && upsert
    EsClient.update(Settings.Notification.EsIndex, device, true, false)
  }

  def unregister(storeCode: String, regId: String): Boolean = {
    val req = search in Settings.Notification.EsIndex types "Device" filter {
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
    implicit val system = ActorSystem()
    import system.dispatcher
    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.
    import com.mogobiz.utils.SSLImplicits.trustfulSslContext

    val pipeline: SendReceive = (
      addHeader("Content-Type", "application/json")
        ~> addCredentials(BasicHttpCredentials(s"key=${Settings.Notification.Gcm.ApiKey}"))
        ~> sendReceive
      )

    val MaxNotifs = 1000
    val toSendIds = if (regIds.length > MaxNotifs) regIds.take(MaxNotifs) else regIds
    val payload = JacksonConverter.mapper.writeValueAsString(GCMRequest(toSendIds, data.toString))
    val res = pipeline(Post("https://android.googleapis.com/gcm/send", payload))
    if (regIds.length > MaxNotifs)
      gcmNotify(regIds.drop(MaxNotifs), data)
    else
      res
  }

  lazy val appleNotificationServer: AppleNotificationServer = {
    new AppleNotificationServerBasicImpl(
      Settings.Notification.Apns.Keystore,
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

    implicit val system = ActorSystem()
    import system.dispatcher
    // Place a special SSLContext in scope here to be used by HttpClient.
    // It trusts all server certificates.
    import com.mogobiz.utils.SSLImplicits.trustfulSslContext

    val MaxNotifs = 1000
    val res =
      Future {
        val jsonData = JacksonConverter.mapper.writeValueAsString(APNSRequest(APNSContent(1, data.toString)))
        val payload = new PushNotificationPayload(jsonData)
        val pushManager = new PushNotificationManager()
        pushManager.initializeConnection(appleNotificationServer)

        val toSendIds = if (regIds.length > MaxNotifs) regIds.take(MaxNotifs) else regIds
        val devices = toSendIds.map(new BasicDevice(_))
        val notifications = pushManager.sendNotifications(payload, devices: _*)
        val successCount = PushedNotification.findSuccessfulNotifications(notifications).size()
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

