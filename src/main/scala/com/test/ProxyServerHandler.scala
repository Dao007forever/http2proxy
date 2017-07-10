package com.test

import org.slf4j.LoggerFactory
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http2.DefaultHttp2LocalFlowController.DEFAULT_WINDOW_UPDATE_RATIO
import io.netty.handler.codec.http2._
import io.netty.handler.logging.LogLevel

import scala.collection._
import scala.collection.convert.decorateAsScala._
import java.util.concurrent.ConcurrentHashMap

class ProxyServerHandler(
  isServer: Boolean,
  decoder: Http2ConnectionDecoder,
  encoder: Http2ConnectionEncoder,
  initialSettings: Http2Settings)
  extends AbstractNettyHandler(decoder, encoder, initialSettings) {

  @volatile
  private var toClient: AbstractNettyHandler = null

  private val streamIdToOtherSide: concurrent.Map[Int, AbstractNettyHandler] = new ConcurrentHashMap[Int, AbstractNettyHandler]().asScala
  private val shardToOtherSide: concurrent.Map[CharSequence, AbstractNettyHandler] = new ConcurrentHashMap[CharSequence, AbstractNettyHandler]().asScala

  // Set the frame listener on the decoder.
  decoder().frameListener(new FrameListener())

  private class FrameListener extends Http2FrameAdapter {

    private[this] final def copyOnHeap(bb: ByteBuf): ByteBuf = {
      if (bb.readableBytes > 0) Unpooled.buffer(bb.readableBytes, bb.capacity).writeBytes(bb)
      else Unpooled.EMPTY_BUFFER
    }

    @throws[Http2Exception]
    override def onDataRead(ctx: ChannelHandlerContext, streamId: Int, content: ByteBuf, padding: Int, endOfStream: Boolean): Int = {
      val buffered = copyOnHeap(content)
      val readableBytes = buffered.readableBytes()
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writeData(otherSide.ctx, streamId, buffered, padding, endOfStream, otherSide.ctx.channel().newPromise())
      if (endOfStream) {
        otherSide.encoder.flowController().writePendingBytes()
        otherSide.ctx.flush()
      }

      readableBytes
    }

    @throws[Http2Exception]
    override def onHeadersRead(
      ctx: ChannelHandlerContext,
      streamId: Int,
      headers: Http2Headers,
      streamDependency: Int,
      weight: Short,
      exclusive: Boolean,
      padding: Int,
      endOfStream: Boolean
    ): Unit = {
      if (headers.authority() != null) {
        headers.authority("localhost:50051")
      }
      if (isServer && !streamIdToOtherSide.contains(streamId)) {
        val shard = headers.get("shard")
        if (shard == null) {
          println(headers)
          println("WTF")
        }
        if (!shardToOtherSide.contains(shard)) {
          val client = ProxyServerHandler(false, 0, 1000000, 10000, 6000000)
          client.toClient = ProxyServerHandler.this
          new ProxyToServer("localhost", 50051, client)
          shardToOtherSide.put(shard, client)
        }
        val client = shardToOtherSide(shard)
        streamIdToOtherSide.put(streamId, client)
        println(s"Put $streamId")
      }
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writeHeaders(otherSide.ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream, otherSide.ctx.channel().newPromise())
      if (endOfStream) {
        otherSide.encoder.flowController().writePendingBytes()
        otherSide.ctx.flush()
        streamIdToOtherSide.remove(streamId)
        println(s"Removed $streamId")
      }
      ()
    }

    @throws[Http2Exception]
    override def onHeadersRead(
      ctx: ChannelHandlerContext,
      streamId: Int,
      headers: Http2Headers,
      padding: Int,
      endOfStream: Boolean
    ): Unit = {
      if (headers.authority() != null) {
        headers.authority("localhost:50051")
      }
      if (isServer && !streamIdToOtherSide.contains(streamId)) {
        val shard = headers.get("shard")
        if (shard == null) {
          println(headers)
          println("WTF")
        }
        if (!shardToOtherSide.contains(shard)) {
          val client = ProxyServerHandler(false, 0, 1000000, 10000, 6000000)
          client.toClient = ProxyServerHandler.this
          new ProxyToServer("localhost", 50051, client)
          shardToOtherSide.put(shard, client)
        }
        val client = shardToOtherSide(shard)
        streamIdToOtherSide.put(streamId, client)
        println(s"Put $streamId")
      }
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writeHeaders(otherSide.ctx, streamId, headers, padding, endOfStream, otherSide.ctx.channel().newPromise())
      if (endOfStream) {
        otherSide.encoder.flowController().writePendingBytes()
        otherSide.ctx.flush()
        streamIdToOtherSide.remove(streamId)
        println(s"Removed $streamId")
      }
      ()
    }

    @throws[Http2Exception]
    override def onRstStreamRead(ctx: ChannelHandlerContext, streamId: Int, errorCode: Long): Unit = {
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writeRstStream(otherSide.ctx, streamId, errorCode, otherSide.ctx.channel().newPromise())
    }

    override def onGoAwayRead(ctx: ChannelHandlerContext, lastStreamId: Int, errorCode: Long, debugData: ByteBuf): Unit = {
      val otherSide = if (isServer) streamIdToOtherSide(lastStreamId) else toClient
      otherSide.encoder.writeGoAway(otherSide.ctx, lastStreamId, errorCode, copyOnHeap(debugData), otherSide.ctx.channel().newPromise())
    }

    override def onPingRead(ctx: ChannelHandlerContext, data: ByteBuf): Unit = {
    }

    override def onPingAckRead(ctx: ChannelHandlerContext, data: ByteBuf): Unit = {
    }

    override def onPriorityRead(ctx: ChannelHandlerContext, streamId: Int, streamDependency: Int, weight: Short, exclusive: Boolean): Unit = {
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writePriority(otherSide.ctx, streamId, streamDependency, weight, exclusive, otherSide.ctx.channel().newPromise())
    }

    override def onPushPromiseRead(ctx: ChannelHandlerContext, streamId: Int, promisedStreamId: Int, headers: Http2Headers, padding: Int): Unit = {
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writePushPromise(otherSide.ctx, streamId, promisedStreamId, headers, padding, otherSide.ctx.channel().newPromise())
    }

    override def onSettingsRead(ctx: ChannelHandlerContext, settings: Http2Settings): Unit = {
    }

    override def onSettingsAckRead(ctx: ChannelHandlerContext): Unit = {
    }

    override def onWindowUpdateRead(ctx: ChannelHandlerContext, streamId: Int, windowSizeIncrement: Int): Unit = {
    }

    override def onUnknownFrame(ctx: ChannelHandlerContext, frameType: Byte, streamId: Int, flags: Http2Flags, payload: ByteBuf): Unit = {
      val otherSide = if (isServer) streamIdToOtherSide(streamId) else toClient
      otherSide.encoder.writeFrame(otherSide.ctx, frameType, streamId, flags, payload, otherSide.ctx.channel().newPromise())
    }
  }
}

object ProxyServerHandler {
  val logger = LoggerFactory.getLogger(classOf[Nothing].getName)

  def apply(
    isServer: Boolean,
    maxStreams: Int,
    flowControlWindow: Int,
    maxHeaderListSize: Int,
    maxMessageSize: Int
  ): ProxyServerHandler = {
    val frameLogger = new Http2FrameLogger(LogLevel.DEBUG, classOf[ProxyServer])
    val headersDecoder = new DefaultHttp2HeadersDecoder(true, 32)
    try
      headersDecoder.maxHeaderListSize(maxHeaderListSize, maxHeaderListSize)
    catch {
      case e: Throwable =>
        throw new RuntimeException(e)
    }
    val frameReader = new Http2InboundFrameLogger(
      new DefaultHttp2FrameReader(headersDecoder),
      frameLogger
    )
    val frameWriter = new Http2OutboundFrameLogger(
      new DefaultHttp2FrameWriter(),
      frameLogger
    )

    apply(isServer, frameReader, frameWriter, maxStreams, maxHeaderListSize, flowControlWindow);
  }

  def apply(
    isServer: Boolean,
    frameReader: Http2FrameReader,
    frameWriter: Http2FrameWriter,
    maxStreams: Int,
    maxHeaderListSize: Int,
    flowControlWindow: Int
  ): ProxyServerHandler = {
    val connection = new DefaultHttp2Connection(isServer)

    connection.local().flowController(
      new DefaultHttp2LocalFlowController(
        connection,
        DEFAULT_WINDOW_UPDATE_RATIO,
        true
      )
    )
    val encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter)
    val decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader)

    val settings = new Http2Settings
    settings.initialWindowSize(flowControlWindow)
    settings.maxConcurrentStreams(maxStreams)
    if (!isServer) {
      settings.pushEnabled(false)
      settings.maxHeaderListSize(maxHeaderListSize)
    }

    new ProxyServerHandler(isServer, decoder, encoder, settings)
  }
}
