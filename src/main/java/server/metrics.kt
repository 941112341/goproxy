package server

import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory

// bit map 更好
enum class LogStatus(val inFlush:Boolean, val inReadComplete: Boolean) {
    Read(false, true), Write(true, false), ReadWrite(true, true);

}

// timeout need be set
class MetricsHandler(val timeout: Long, val status: LogStatus): ChannelHandlerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private var lastStep: Long = 0

    override fun channelActive(ctx: ChannelHandlerContext?) {
        lastStep = System.currentTimeMillis()
        super.channelActive(ctx)
    }

    override fun flush(ctx: ChannelHandlerContext?) {
        val now = System.currentTimeMillis()
        val since = now - lastStep
        if (status.inFlush) {
            if (since < timeout) {
                log.info("flush use {} mills, channel {}, uri {}",
                        since, channelId(ctx!!.channel()), poolUrl(ctx.channel()))
            } else {
                log.warn("flush timeout 2000, use {} mills, channel {}, uri {}",
                        since, channelId(ctx!!.channel()), poolUrl(ctx.channel()))
            }
        }
        lastStep = now
        super.flush(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val now = System.currentTimeMillis()
        val since = now - lastStep
        if (status.inReadComplete) {
            if (since < timeout) {
                log.info("read use {} mills, channel {}, uri {}",
                        since, channelId(ctx!!.channel()), poolUrl(ctx.channel()))
            } else {
                log.warn("read timeout 2000, use {} mills, channel {}, uri {}",
                        since, channelId(ctx!!.channel()), poolUrl(ctx.channel()))
            }
        }
        lastStep = now
        super.channelRead(ctx, msg)
    }
}

