/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.config

import com.typesafe.config.ConfigFactory

object Settings {
  private val config = ConfigFactory.load("notify").withFallback(ConfigFactory.load("default-notify"))

  object Notification {
    val EsIndex = config.getString("notification.esindex")

    object Gcm {
      val ApiKey = config.getString("notification.gcm.key")
    }

    object Apns {
      val Keystore     = config.getString("notification.apns.keystore.name")
      val Password     = config.getString("notification.apns.password")
      val KeystoreType = config.getString("notification.apns.keystore.type")
      val Host         = config.getString("notification.apns.host")
      val Port         = config.getString("notification.apns.port")
      val TokenSize    = config.getInt("notification.apns.token.size")
    }

  }
}
