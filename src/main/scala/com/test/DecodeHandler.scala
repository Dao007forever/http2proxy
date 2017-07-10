package com.test

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelDuplexHandler, ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http2._
import io.netty.handler.logging.LogLevel

@ChannelHandler.Sharable
class DecodeHandler extends ChannelDuplexHandler {
  private val HTTP2_FRAME_LOGGER = new Http2FrameLogger(LogLevel.INFO, classOf[DecodeHandler])

  @throws[Exception]
  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val byteBuf = msg.asInstanceOf[ByteBuf]
    val copy = byteBuf.copy
    val headersDecoder = new DefaultHttp2HeadersDecoder(true, 32)
    try
      headersDecoder.maxHeaderListSize(1000, 1000)
    catch {
      case e: Throwable =>
        throw new RuntimeException(e)
    }
    val frameReader = new DefaultHttp2FrameReader(headersDecoder)
    frameReader.maxFrameSize(6000000)
    val reader = new Http2InboundFrameLogger(
      frameReader,
      HTTP2_FRAME_LOGGER)
    println(byteBuf.toString())
    reader.readFrame(ctx, byteBuf, new Http2FrameAdapter() {
      @throws[Http2Exception]
      override def onHeadersRead(
        ctx: ChannelHandlerContext,
        streamId: Int,
        headers: Http2Headers,
        streamDependency: Int,
        weight: Short,
        exclusive: Boolean,
        padding: Int,
        endStream: Boolean
      ): Unit = {
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream)
        println(headers)
      }

      override def onDataRead(
        ctx: ChannelHandlerContext,
        streamId: Int,
        data: ByteBuf,
        padding: Int,
        endOfStream: Boolean
      ): Int = {
        println(data.toString())
        super.onDataRead(ctx, streamId, data, padding, endOfStream)
      }

      override def onUnknownFrame(ctx: ChannelHandlerContext, frameType: Byte, streamId: Int, flags: Http2Flags, payload: ByteBuf): Unit = {
        super.onUnknownFrame(ctx, frameType, streamId, flags, payload)
        println("UNKNOWN")
        println(frameType)
        println(payload.toString())
      }
    })
    byteBuf.release
    ctx.fireChannelRead(copy)
  }

  @throws[Exception]
  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.fireChannelReadComplete
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
  }
}
