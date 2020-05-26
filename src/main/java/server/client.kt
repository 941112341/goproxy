package server

import gen.Message
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit



class IdleProxyClient(r: Long, w: Long, rw: Long, timeUnit: TimeUnit): IdleStateHandler(r, w, rw, timeUnit) {

    val log = LoggerFactory.getLogger(javaClass)

    override fun channelIdle(ctx: ChannelHandlerContext?, evt: IdleStateEvent?) {
        if (evt == IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT) {
            val channel = ctx!!.channel()
            if (channel.isActive) {
                channel.writeAndFlush(IdleMessage())
                log.info("this socket {} is active, uri {}", channel.attr(unionId).get(), channel.attr(pool).get()?.uri)
            } else {
                log.info("this socket {} is inactive", channel.attr(unionId).get())
            }
        } else if (evt == IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT) {
            val channel = ctx!!.channel()
            val channelPool = channel.attr(pool).get()
            channelPool.remove(channel)
            channel.attr(pool).remove()
            channel.attr(realClient).remove()
            log.info("idle timeout, close channel {}, pool {} socketCnt {}", channel.attr(unionId).get(), channelPool.uri, channelPool.socketCnt--)
        }
        super.channelIdle(ctx, evt)
    }

}

fun IdleMessage():Message.Request {
    return Message.Request.getDefaultInstance()
}