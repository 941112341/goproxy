package client

import common.*
import gen.Message
import gen.wrapValue
import gen.wrapValues
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
import java.lang.RuntimeException


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
                        channel.writeAndFlush(msg.wrapValues(destService.otherParameter))
                        channel.attr(realClient).set(realChannel)
                    }
                }
            })
        } else {
            // 服务降级
            realChannel.writeAndFlush(notFoundResponse)
        }
    }

    fun close() {
        group.shutdownGracefully()
    }

    fun test() {
        bootstarp.connect("127.0.0.1", 8888).addListener(object : ChannelFutureListener{
            override fun operationComplete(future: ChannelFuture?) {
                if (!future!!.isSuccess) {
                    throw RuntimeException(future.cause())
                }
                future.channel()!!.writeAndFlush(
                     Message.Request.newBuilder().setCtx(
                            Message.Context.newBuilder().putAllMaps(mutableMapOf()).build()
                     ).setParameter("""{
                         | "boy":"123"
                         |}""".trimMargin()).build()
                )
            }
        })
//        close()
    }
}

