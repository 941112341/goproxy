package server

import com.alibaba.fastjson.JSON
import gen.Message
import gen.getValue
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoop
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


object ChannelManager {

    val nameChannelMap = mutableMapOf<String, SocketChannelPool>()

    fun close() {
        nameChannelMap.forEach { _, u -> u.close() }
    }

    fun transfer(uri: String, realChannel: Channel, msg: Message.Request) {
        val channelPool = nameChannelMap[uri] ?: let {
            synchronized(nameChannelMap) {
                var pool = nameChannelMap[uri]
                if (pool == null) {
                    pool = SocketChannelPool(uri = uri)
                    nameChannelMap[uri] = pool
                }
            }

            nameChannelMap[uri]
        }

        channelPool!!.transfer(realChannel, msg)
    }
}

class SocketChannelPool(private val channelQueue:ConcurrentLinkedQueue<SocketChannel> = ConcurrentLinkedQueue<SocketChannel>(),
                        private val uri:String)
    :Queue<SocketChannel> by channelQueue {
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

                pipeline.addLast(ProtoClient(this@SocketChannelPool))
            }
        })
    }

    fun transfer(realChannel: Channel, msg: Message.Request) {
        val channel = this.poll()
        if (channel == null) {
            val (host, part) = getTarget()
            bootstarp.connect(host, part).addListener(object: ChannelFutureListener {
                override fun operationComplete(future: ChannelFuture?) {
                    if (!future!!.isSuccess) {
                        future.cause().printStackTrace()
                        realChannel.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE)
                    } else {
                        val channel = future.channel()
                        channel.attr(realClient).set(realChannel)
                        channel.writeAndFlush(msg)
                    }
                }
            })
        } else {
            channel.attr(realClient).set(realChannel)
            channel.writeAndFlush(msg)
        }
    }

    fun getTarget():Pair<String, Int> {
        return Pair("127.0.0.1", 8888)
    }

    fun close() {
        group.shutdownGracefully()
    }
}

class ProtoClient(val socketChannelPool: SocketChannelPool): SimpleChannelInboundHandler<Message.Response>() {
    override fun messageReceived(ctx: ChannelHandlerContext?, msg: Message.Response?) {
        val channel = ctx!!.channel()
        val protoMsg = msg!!

        val alloc = channel.alloc()
        val buffer = alloc.buffer()
        buffer.writeBytes(protoMsg.data.toByteArray())

        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)

        val header = protoMsg.ctx.getValue("http-header")
        val headers = JSON.parseObject(header, Map::class.java)
        headers.forEach { resp.headers()[it.key.toString()] = it.value.toString()}

        val rc = channel.attr(realClient).get()!!
        rc.writeAndFlush(resp).addListener(object : ChannelFutureListener {
            override fun operationComplete(future: ChannelFuture?) {
                future!!.channel().close()
                if (channel is SocketChannel) {
                    socketChannelPool.add(channel)
                } else {
                    println("channel is not socket channel")
                }
            }
        })
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        cause!!.printStackTrace()
    }
}

