package server

import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener


fun writeError(channel: Channel) {
    channel.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE)
}

fun channelId(channel: Channel):String {
    return channel.attr(unionId).get()
}

fun poolUrl(channel: Channel):String {
    return channel.attr(pool)?.get()?.uri ?: "null"

}