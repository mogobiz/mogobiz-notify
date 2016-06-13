/*
 * Copyright (C) 2015 Mogobiz SARL. All rights reserved.
 */

package com.mogobiz.notify.es

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.mogobiz.es.EsClient
import com.mogobiz.notify.config.Settings
import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Mapping {
  def clear = EsClient().execute(delete index Settings.Notification.EsIndex).await

  def mappingNames = List()

  def set() {
    def route(url: String) = "http://" + com.mogobiz.es.Settings.ElasticSearch.FullUrl + url
    def mappingFor(name: String) = getClass().getResourceAsStream(s"es/notify/mappings/$name.json")

    implicit val system = akka.actor.ActorSystem("mogopay-boot")


    mappingNames foreach { name =>
      val url = s"/${Settings.Notification.EsIndex}/$name/_mapping"
      val mapping = scala.io.Source.fromInputStream(mappingFor(name)).mkString

      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(route(url)),
        entity = HttpEntity(MediaTypes.`application/json`, mapping)
      )
      val singleResult: Future[Unit] = Http().singleRequest(request).map { response: HttpResponse =>

        response.status match {
          case StatusCodes.OK => System.err.println(s"The mapping for `$name` was successfully set.")

          case _ =>
            // System.err.println(s"Error while setting the mapping for `$name`: ${response.entity.toStrict(5 seconds).map(_.data.toString())}")
            Unmarshal(response.entity).to[String].map { data =>
              System.err.println(s"Error while setting the mapping for `$name`: ${data}")
            }
        }
      }
      Await.result(singleResult, 10 seconds)
    }

    system.shutdown()
  }
}
