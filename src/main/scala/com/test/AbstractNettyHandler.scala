package com.test

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http2._
import io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception

class AbstractNettyHandler(decoder: Http2ConnectionDecoder, encoder: Http2ConnectionEncoder, initialSettings: Http2Settings)
  extends Http2ConnectionHandler(decoder, encoder, initialSettings) {
  private val GRACEFUL_SHUTDOWN_TIMEOUT = 5000 // ms
  private var initialConnectionWindow = 1
  @volatile
  var ctx: ChannelHandlerContext = null

  // Set the timeout for graceful shutdown.
  gracefulShutdownTimeoutMillis(GRACEFUL_SHUTDOWN_TIMEOUT)
  // Extract the connection window from the settings if it was set.
  this.initialConnectionWindow = if (initialSettings.initialWindowSize == null) -1
  else initialSettings.initialWindowSize

  @throws[Exception]
  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    this.ctx = ctx
    // Sends the connection preface if we haven't already.
    super.handlerAdded(ctx)
    sendInitialConnectionWindow()
  }

  @throws[Exception]
  override def channelActive(ctx: ChannelHandlerContext): Unit = { // Sends connection preface if we haven't already.
    super.channelActive(ctx)
    sendInitialConnectionWindow()
  }

  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val embedded = getEmbeddedHttp2Exception(cause)
    if (embedded == null) { // There was no embedded Http2Exception, assume it's a connection error. Subclasses are
      // responsible for storing the appropriate status and shutting down the connection.
      onError(ctx, cause)
    }
    else super.exceptionCaught(ctx, cause)
  }

  /**
    * Sends initial connection window to the remote endpoint if necessary.
    */
  @throws[Http2Exception]
  private def sendInitialConnectionWindow() = {
    if (ctx.channel.isActive && initialConnectionWindow > 0) {
      val connectionStream = connection.connectionStream
      val currentSize = connection.local.flowController.windowSize(connectionStream)
      val delta = initialConnectionWindow - currentSize
      decoder.flowController.incrementWindowSize(connectionStream, delta)
      initialConnectionWindow = -1
      ctx.flush
    }
  }
}
