package com.mogobiz.notify

import java.io.File

import com.typesafe.config.ConfigFactory

object Settings {
  private val config = ConfigFactory.load()


  object Gcm {
    val ApiKey = config.getString("notification.gcm.key")
  }

  object Apns {
    val Keystore = config.getString("notification.apns.keystore.name")
    val Password = config.getString("notification.apns.password")
    val KeystoreType = config.getString("notification.apns.keystore.type")
    val Host = config.getString("notification.apns.host")
    val Port = config.getString("notification.apns.port")
    val TokenSize = config.getInt("notification.apns.token.size")

  }


}

