package server

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class IdleProxyClient(r: Long, w: Long, rw: Long, timeUnit: TimeUnit): IdleStateHandler(r, w, rw, timeUnit) {

    override fun channelIdle(ctx: ChannelHandlerContext?, evt: IdleStateEvent?) {
        if (evt == Idle)
    }
}