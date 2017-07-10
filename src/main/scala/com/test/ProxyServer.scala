package com.test

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelInitializer, ChannelOption, EventLoopGroup}
import io.netty.handler.logging.{LogLevel, LoggingHandler}

class ProxyServer(val port: Int) {
  private val group: EventLoopGroup = new NioEventLoopGroup
  private var channel: Channel = null

    def start(): Unit = {
    try {
      val b = new ServerBootstrap
      b.option(ChannelOption.SO_BACKLOG, new Integer(1024))
      b.group(group)
        .channel(classOf[NioServerSocketChannel])
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ProxyServerInitializer)
      channel = b.bind(port).sync.channel
      println("Proxy server start success")
      channel.closeFuture.sync
    } catch {
      case e: InterruptedException =>
        e.printStackTrace()
    }
  }

  def awaitTermination(): Unit = {
  }

  def stop(): Unit = {
    group.shutdownGracefully()
  }
}

class ProxyServerInitializer extends ChannelInitializer[SocketChannel] {
  @throws[Exception]
  protected def initChannel(ch: SocketChannel): Unit = {
    val server = ProxyServerHandler(true, Int.MaxValue, 1000000, 10000, 6000000)

    ch.pipeline()
      .addLast(
        new LoggingHandler(),
        server
      )

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        System.err.println("*** shutting down proxy server since JVM is shutting down")
        server.ctx.channel().close().awaitUninterruptibly()
        System.err.println("*** server shut down")
      }
    })
  }
}

object ProxyServer extends App {
  try {
    new ProxyServer(50000).start()
  } finally {

  }
}
