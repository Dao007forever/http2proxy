package com.test

import java.util.concurrent.{Executors, TimeUnit}
import java.util.logging.{Level, Logger}

import com.test.greeter.{GreeterGrpc, HelloRequest}
import io.grpc._

import scala.language.implicitConversions
import scala.util.Random

object CustomHeaderClient {
  val logger: Logger = Logger.getLogger(classOf[CustomHeaderClient].getName)

  val executors = Executors.newFixedThreadPool(100)

  val rand = new Random()

  def randomShard: String = rand.nextInt(100).toString

  implicit def runnable(f: => Unit): Runnable = new Runnable() { def run() = f }

  /**
    * Main start the client from the command line.
    */
  @throws(classOf[Exception])
  def main(args: Array[String]): Unit = {
    executors.execute(testClient(args, Some("0")))
  }

  def testClient(args: Array[String], shardId: Option[String] = None) = {
    val client = new CustomHeaderClient("localhost", 50000, shardId.getOrElse(randomShard))
    try {
      val user = args.headOption.getOrElse("world")
      while (true) {
        client.greet(user)
        Thread.sleep(200)
      }
    } finally {
      client.shutdown()
    }
  }
}

/**
  * A custom client.
  */
class CustomHeaderClient(host: String, port: Int, shard: => String) {
  private final val originChannel: ManagedChannel =
    ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
  private final val blockingStub: GreeterGrpc.GreeterBlockingStub = {
    val interceptor = new HeaderClientInterceptor(shard)
    val channel = ClientInterceptors.intercept(originChannel, interceptor)
    GreeterGrpc.blockingStub(channel)
  }

  private def shutdown(): Unit = {
    originChannel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  /**
    * A simple client method that like [[io.grpc.examples.helloworld.HelloWorldClient]].
    */
  private def greet(name: String): Unit = {
    CustomHeaderClient.logger.info("Will try to greet " + name + " ...")
    val request = HelloRequest(name)
    val response = try {
      blockingStub.sayHello(request)
    } catch {
      case e: StatusRuntimeException =>
        CustomHeaderClient.logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus)
        return
    }
    CustomHeaderClient.logger.info("Greeting: " + response.message)
  }
}

/**
  * A interceptor to handle client header.
  */
object HeaderClientInterceptor {
  private val logger: Logger = Logger.getLogger(classOf[HeaderClientInterceptor].getName)
  private val customHeadKey: Metadata.Key[String] = Metadata.Key.of(
    "Shard",
    Metadata.ASCII_STRING_MARSHALLER
  )
}

class HeaderClientInterceptor(shard: => String) extends ClientInterceptor {
  def interceptCall[ReqT, RespT](
                                  method: MethodDescriptor[ReqT, RespT],
                                  callOptions: CallOptions,
                                  next: Channel
                                ): ClientCall[ReqT, RespT] = {
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata) = {
        headers.put(HeaderClientInterceptor.customHeadKey, shard)
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener[RespT](responseListener) {
          override def onHeaders(headers: Metadata) = {
            HeaderClientInterceptor.logger.info("header received from server:" + headers)
            super.onHeaders(headers)
          }
        }, headers)
      }
    }
  }
}
