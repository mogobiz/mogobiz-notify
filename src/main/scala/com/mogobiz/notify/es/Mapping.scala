/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.es

import com.mogobiz.es.EsClient
import com.mogobiz.notify.config.Settings
import com.sksamuel.elastic4s.ElasticDsl._
import spray.client.pipelining._
import spray.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Mapping {
  import EsClient.secureRequest
  def clear = EsClient().execute(secureRequest(delete index Settings.Notification.EsIndex)).await

  def mappingNames = List()

  def set() {
    def route(url: String)       = "http://" + com.mogobiz.es.Settings.ElasticSearch.FullUrl + url
    def mappingFor(name: String) = getClass().getResourceAsStream(s"es/notify/mappings/$name.json")

    implicit val system                                                = akka.actor.ActorSystem("mogopay-boot")
    val pipeline: HttpRequest => scala.concurrent.Future[HttpResponse] = sendReceive

    mappingNames foreach { name =>
      val url     = s"/${Settings.Notification.EsIndex}/$name/_mapping"
      val mapping = scala.io.Source.fromInputStream(mappingFor(name)).mkString
      val x: Future[Any] = pipeline(Post(route(url), mapping)) map { response: HttpResponse =>
        response.status match {
          case StatusCodes.OK => System.err.println(s"The mapping for `$name` was successfully set.")
          case _              => System.err.println(s"Error while setting the mapping for `$name`: ${response.entity.asString}")
        }
      }
      Await.result(x, 10 seconds)
    }

    system.shutdown()
  }
}
