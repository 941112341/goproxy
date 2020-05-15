package server

import gen.Message
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import java.nio.charset.Charset
import java.util.*

class CoreServer: SimpleChannelInboundHandler<FullHttpRequest> () {

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

        val channel = borrowChannel(uri)

        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer("hello world", Charset.defaultCharset()))
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
        ctx!!.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        cause?.printStackTrace()
    }
}

