package com.test

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel

class ProxyToServer(host: String, port: Int, clientHandler: AbstractNettyHandler) {
  val workerGroup = new NioEventLoopGroup()
  val initializer = new Http2ClientInitializer(clientHandler)

  try {
    val b = new Bootstrap()
      .group(workerGroup)
      .channel(classOf[NioSocketChannel])
      .option(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
      .remoteAddress(host, port)
      .handler(initializer)

    b.connect().syncUninterruptibly().channel()
    println(s"Connected to $host:$port")
  }
}
