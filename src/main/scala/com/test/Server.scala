package com.test

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.test.greeter.{GreeterGrpc, HelloReply, HelloRequest}
import com.typesafe.scalalogging.slf4j.LazyLogging
import io.grpc._
import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.{WebServer, WebServerConfig}
import org.slf4j.MDC

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls
import scala.util.Random


/**
  * Runs the sync server.
  */
class TestServer(args: Array[String]) extends LazyLogging {
  implicit val actorSystem = ActorSystem("Test-ActorSystem")
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  MDC.put("appName", "testServer")

  val rand = new Random()

  val routes = Routes({
    case HttpRequest(request) => request match {
      case GET(Path("/healthz")) =>
        request.response.write("OK", "application/text")
      case GET(Path("/time")) =>
        if (rand.nextInt(10) <= 2) {
          // Fail 30% of the time
          request.response.write(HttpResponseStatus.INTERNAL_SERVER_ERROR)
        } else {
          request.response.write(System.currentTimeMillis().toString, "application/text")
        }
    }
  })

  //noinspection FieldFromDelayedInit
  val webServer = new WebServer(
    WebServerConfig().copy(
      hostname = "0.0.0,0", // Bind to all interfaces
      port = 8085),
    routes, actorSystem)

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run() {
      logger.info("stopping")
      webServer.stop()
      logger.info("stopped")
    }
  })

  webServer.start()
}

object HelloWorldServer {
  import org.slf4j.LoggerFactory

  private val logger = LoggerFactory.getLogger(classOf[HelloWorldServer].getName)

  private val port = 50051
}

class HelloWorldServer(executionContext: ExecutionContext) { self =>
  private[this] var server: Server = null

  def start(): Unit = {
    server = ServerBuilder.forPort(HelloWorldServer.port)
      .addService(GreeterGrpc.bindService(new GreeterImpl, executionContext))
      .build.start
    HelloWorldServer.logger.info("Server started, listening on " + HelloWorldServer.port)
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        self.stop()
        System.err.println("*** server shut down")
      }
    })
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class GreeterImpl extends GreeterGrpc.Greeter {
    override def sayHello(req: HelloRequest) = {
      println("HEEEEEEERRRRRREEEEE")
      val reply = HelloReply(message = "Hello " + req.name)
      Future.successful(reply)
    }
  }

}

object TestServer extends App {
  val server = new HelloWorldServer(ExecutionContext.global)
  server.start()
  new TestServer(args)
  server.blockUntilShutdown()
}

