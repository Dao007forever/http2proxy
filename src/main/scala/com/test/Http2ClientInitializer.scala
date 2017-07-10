package com.test

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http2._
import io.netty.handler.logging.{LogLevel, LoggingHandler}

class Http2ClientInitializer(framer: AbstractNettyHandler) extends ChannelInitializer[SocketChannel] {

  @throws[Exception]
  override def initChannel(ch: SocketChannel) {
    ch.pipeline().addLast(
      new LoggingHandler(),
      framer
    )
  }
}

object Http2ClientInitializer {
  val logger = new Http2FrameLogger(LogLevel.INFO, classOf[Http2ClientInitializer])
}
