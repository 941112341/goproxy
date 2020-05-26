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
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


object ChannelManager {

    private val nameChannelMap = mutableMapOf<String, SocketChannelPool>()
    private val scheduledExecutorService = ScheduledThreadPoolExecutor(3)
    private val log = LoggerFactory.getLogger(javaClass)

    fun close() {
        nameChannelMap.forEach { _, u -> u.close() }
    }

    fun transfer(uri: String, realChannel: Channel, msg: Message.Request) {
        log.info("uuid {} => uri {}", realChannel.attr(unionId).get(), uri)

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

    fun debug() {
        scheduledExecutorService.scheduleAtFixedRate(
                {
                    nameChannelMap.forEach { t, u ->
                        u.debug()
                    }
                }, 30, 30, TimeUnit.SECONDS
        )
    }
}

class SocketChannelPool(private val channelQueue:ConcurrentLinkedQueue<SocketChannel> = ConcurrentLinkedQueue<SocketChannel>(),
                        val uri:String)
    :Queue<SocketChannel> by channelQueue {
    private val bootstarp = Bootstrap()
    private val group = NioEventLoopGroup()
    @Volatile var socketCnt = 0

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        bootstarp.group(group).channel(NioSocketChannel::class.java)
        bootstarp.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel?) {
                val pipeline = ch!!.pipeline()
                pipeline.addLast(ProtobufVarint32FrameDecoder())
                pipeline.addLast(ProtobufDecoder(Message.Response.getDefaultInstance()))
                pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                pipeline.addLast(ProtobufEncoder())
                pipeline.addLast(IdleProxyClient(defaultReadTimeout, defaultWriteTimeout,
                        0, TimeUnit.SECONDS))
                pipeline.addLast(ProtoClient(this@SocketChannelPool))
            }
        })
    }

    fun transfer(realChannel: Channel, msg: Message.Request) {
        val channel = channelQueue.poll()
        if (channel == null) {
            if (socketCnt > maxSocketNum) {
                writeError(realChannel)
                return
            }
            val (host, part) = getTarget()
            bootstarp.connect(host, part).addListener(object: ChannelFutureListener {
                override fun operationComplete(future: ChannelFuture?) {
                    if (!future!!.isSuccess) {
                        future.cause().printStackTrace()
                        realChannel.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE)
                    } else {
                        val channel = future.channel()
                        channel.attr(realClient).set(realChannel)
                        channel.attr(pool).set(this@SocketChannelPool)
                        val uuid = UUID.randomUUID().toString()
                        channel.attr(unionId).set(uuid)
                        channel.writeAndFlush(msg)
                        log.info("connector {} => real {}, uri {}, socketCnt {}", uuid,
                                realChannel.attr(unionId).get(), uri, ++socketCnt)
                    }
                }
            })
        } else {
            channel.attr(realClient).set(realChannel)
            channel.writeAndFlush(msg)
            log.info("connector {} => real {}, uri {}, socketCnt {}", channel.attr(unionId).get(),
                    realChannel.attr(unionId).get(), uri, socketCnt)
        }
    }

    fun getTarget():Pair<String, Int> {
        return Pair("127.0.0.1", 8888)
    }

    fun close() {
        group.shutdownGracefully()
    }

    fun returnChannel(channel: SocketChannel) {
        add(channel)
        channel.attr(realClient).remove()
        log.info("socketNum {} and return socket {} url {}", socketCnt, channel.attr(unionId).get(), channel.attr(pool).get()?.uri)
    }

    fun debug() {
        val list = mutableListOf<String>()
        channelQueue.forEach { list.add(it.attr(unionId).get()) }

        log.info("uri {} queue {} socketCnt {}", uri, JSON.toJSONString(list), socketCnt)
    }
}

class ProtoClient(val socketChannelPool: SocketChannelPool): SimpleChannelInboundHandler<Message.Response>() {

    val log = LoggerFactory.getLogger(javaClass)

    override fun messageReceived(ctx: ChannelHandlerContext?, msg: Message.Response?) {
        val channel = ctx!!.channel()
        val protoMsg = msg!!
        if (protoMsg.ctx == Message.Context.getDefaultInstance()) {
            log.debug("{} receive idle message, uri= {}", channelId(channel), poolUrl(channel))
            return
        }
        // just for debug
       val data = if (protoMsg.data != "") protoMsg.data else "hello world"

        val alloc = channel.alloc()
        val buffer = alloc.buffer()
        buffer.writeBytes(data.toByteArray())

        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)

        val header = protoMsg.ctx.getValue("http-header")
        val headers = JSON.parseObject(header, Map::class.java)
        headers?.forEach { resp.headers()[it.key.toString()] = it.value.toString()}
        val rc = channel.attr(realClient).get()
        if (rc == null || !rc.isActive) {
            log.error("real client not active")
            return
        }
        rc.writeAndFlush(resp)?.addListener(object : ChannelFutureListener {
            override fun operationComplete(future: ChannelFuture?) {

                log.info("return http response channel {} uri {} realChannel {} success? {}", channelId(channel), poolUrl(channel), channelId(rc), future?.isSuccess)

                future!!.channel().close()
                if (channel is SocketChannel) {
                    socketChannelPool.returnChannel(channel)
                } else {
                    log.error("channel is not socket channel")
                }
            }
        })
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        log.info("channel inactive")
        val channel = ctx!!.channel()
        socketChannelPool.remove(channel)
        channel.attr(realClient).get()?.let {
            if (it.isActive) {
                it.close()
            }
        }
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        log.error("exception caught {}", cause)
        super.exceptionCaught(ctx, cause)
    }
}

