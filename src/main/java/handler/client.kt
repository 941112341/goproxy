package handler

import com.alibaba.fastjson.JSON
import common.realClient
import gen.Message
import gen.getValue
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.slf4j.LoggerFactory


class ProtoClient: SimpleChannelInboundHandler<Message.Response>() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun messageReceived(ctx: ChannelHandlerContext?, msg: Message.Response?) {
        val channel = ctx!!.channel()
        val protoMsg = msg!!

        val data = protoMsg.data
        val alloc = channel.alloc()
        val buffer = alloc.buffer()
        buffer.writeBytes(data.toByteArray())

        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)
        // 关闭客户端
        channel.close()

        val header = protoMsg.ctx.getValue("http-header")
        val headers = JSON.parseObject(header, Map::class.java)
        headers?.forEach { resp.headers()[it.key.toString()] = it.value.toString()}
        val rc = channel.attr(realClient).get()
        channel.attr(realClient).remove()
        rc.writeAndFlush(resp)
    }


    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        log.error("exception caught {}", cause)
        ctx?.close()
    }
}
