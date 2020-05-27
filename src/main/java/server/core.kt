package server

import gen.Message
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.*

class CoreServer: SimpleChannelInboundHandler<FullHttpRequest> () {

    private val log = LoggerFactory.getLogger(javaClass)


    override fun channelActive(ctx: ChannelHandlerContext?) {
        val uuid = UUID.randomUUID().toString()
        val channel = ctx!!.channel()
        channel.attr(unionId).set(uuid)
        super.channelActive(ctx)
    }

    override fun messageReceived(ctx: ChannelHandlerContext?, msg: FullHttpRequest?) {
        val req = msg!!
        val uri = req.uri() ?: "/"

        // build
        val body = req.content().toString(Charset.defaultCharset())
        val map = mutableMapOf<String, String>()
        for (header in req.headers()) {
            map[header.key.toString()] = header.value.toString()
        }
        val requestID = UUID.randomUUID()
        map["requestID"] = requestID.toString()
        val context = Message.Context.newBuilder().putAllMaps(map).build()
        val message = Message.Request.newBuilder().setCtx(context).setParameter(body).build()

        ChannelManager.transfer(uri, ctx!!.channel() as SocketChannel, message)

    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        log.error("core exception caught {}", cause)
    }
}

