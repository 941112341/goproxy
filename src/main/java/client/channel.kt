package client

import common.*
import gen.Message
import handler.ProtoClient
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender


object ProtobufClient {

    private val bootstarp = Bootstrap()
    private val group = NioEventLoopGroup()
    init {
        bootstarp.group(group).channel(NioSocketChannel::class.java)
        bootstarp.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel?) {
                val pipeline = ch!!.pipeline()
                pipeline.addLast(ProtobufVarint32FrameDecoder())
                pipeline.addLast(ProtobufDecoder(Message.Response.getDefaultInstance()))
                pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                pipeline.addLast(ProtobufEncoder())

                pipeline.addLast("client", ProtoClient())
            }
        })
    }

    fun transfer(url: String, realChannel: Channel, msg: Message.Request) {
        val destService = Discover.queryMeta(url)
        val hostMeta = Discover.queryHost(destService.urlMeta.serviceName)
        val host = hostMeta.getHost()
        if (host != null) {
            bootstarp.connect(host.first, host.second).addListener(object : ChannelFutureListener {
                override fun operationComplete(future: ChannelFuture?) {
                    if (!future!!.isSuccess) {
                        future.cause().printStackTrace()
                        realChannel.writeAndFlush(errorResponse)
                    } else {
                        val channel = future.channel()
                        channel.writeAndFlush(msg)
                        channel.attr(realClient).set(realChannel)
                    }
                }
            })
        } else {
            // 服务降级
            realChannel.writeAndFlush(errorResponse)
        }
    }

    fun close() {
        group.shutdownGracefully()
    }
}

