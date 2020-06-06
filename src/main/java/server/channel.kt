package server

import com.alibaba.fastjson.JSON
import gen.Message
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
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
                    pool = SocketChannelPool(namespace = uri)
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
                        val namespace:String)
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
                pipeline.addLast("metrics", MetricsHandler(serverTimeout * 1000, LogStatus.Read))
                pipeline.addLast(ProtobufVarint32FrameDecoder())
                pipeline.addLast(ProtobufDecoder(Message.Response.getDefaultInstance()))
                pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                pipeline.addLast(ProtobufEncoder())
                pipeline.addLast(IdleProxyClient(defaultReadTimeout, defaultWriteTimeout,
                        0, TimeUnit.SECONDS))

                pipeline.addLast("client", ProtoClient(this@SocketChannelPool))
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
            val target = getTarget()
            if (target == null) {
                realChannel.writeAndFlush(notFoundResponse)
                return
            }
            val (host, port) = target
            bootstarp.connect(host, port).addListener(object: ChannelFutureListener {
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
                                realChannel.attr(unionId).get(), namespace, ++socketCnt)
                    }
                }
            })
        } else {
            channel.attr(realClient).set(realChannel)
            channel.writeAndFlush(msg)
            log.info("connector {} => real {}, uri {}, socketCnt {}", channel.attr(unionId).get(),
                    realChannel.attr(unionId).get(), namespace, socketCnt)
        }
    }

    private fun getTarget():Pair<String, Int>? {
        return Discover.getSource(namespace)?.toPair()
    }

    fun close() {
        group.shutdownGracefully()
    }

    fun returnChannel(channel: SocketChannel) {
        add(channel)
        channel.attr(realClient).remove()
        log.info("socketNum {} and return socket {} url {}", socketCnt, channel.attr(unionId).get(), channel.attr(pool).get()?.namespace)
    }

    fun debug() {
        val list = mutableListOf<String>()
        channelQueue.forEach { list.add(it.attr(unionId).get()) }

        log.info("uri {} queue {} socketCnt {}", namespace, JSON.toJSONString(list), socketCnt)
    }
}

class IdleProxyClient(r: Long, w: Long, rw: Long, timeUnit: TimeUnit): IdleStateHandler(r, w, rw, timeUnit) {

    val log = LoggerFactory.getLogger(javaClass)

    override fun channelIdle(ctx: ChannelHandlerContext?, evt: IdleStateEvent?) {
        if (evt == IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT) {
            val channel = ctx!!.channel()
            if (channel.isActive) {
                channel.writeAndFlush(idleMessage())
                log.info("this socket {} is active, uri {}", channel.attr(unionId).get(), channel.attr(pool).get()?.namespace)
            } else {
                log.info("this socket {} is inactive", channel.attr(unionId).get())
            }
        } else if (evt == IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT) {
            val channel = ctx!!.channel()
            val channelPool = channel.attr(pool).get()
            channelPool.remove(channel)
            channel.attr(pool).remove()
            channel.attr(realClient).remove()
            log.info("idle timeout, close channel {}, pool {} socketCnt {}", channel.attr(unionId).get(), channelPool.namespace, channelPool.socketCnt--)
        }
        super.channelIdle(ctx, evt)
    }

}
