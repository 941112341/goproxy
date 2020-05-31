package server

import com.alibaba.fastjson.JSON
import gen.Message
import gen.getValue
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.slf4j.LoggerFactory


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
        socketChannelPool.socketCnt--
        channel.attr(realClient).get()?.let {
            if (it.isActive) {
                it.close()
            }
        }
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        log.error("exception caught {}", cause)
    }
}
