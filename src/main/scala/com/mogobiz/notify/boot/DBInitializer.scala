/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.boot

import com.mogobiz.es.EsClient
import com.mogobiz.notify.config.Settings
import com.mogobiz.notify.es.Mapping
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException

object DBInitializer {
  def apply(): Unit =
    try {
      import EsClient.secureRequest
      EsClient().execute(secureRequest(create index Settings.Notification.EsIndex)).await
      Mapping.set()
      fillDB()
    } catch {
      case e: RemoteTransportException if e.getCause().isInstanceOf[IndexAlreadyExistsException] =>
        println(s"Index ${Settings.Notification.EsIndex} was not created because it already exists.")
      case e: Throwable => e.printStackTrace()
    }

  private def fillDB() {}
}
