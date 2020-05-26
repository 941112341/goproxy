package server

import io.netty.channel.Channel
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.AttributeKey

val realClient = AttributeKey.newInstance<Channel>("realClient")!!
val pool = AttributeKey.newInstance<SocketChannelPool>("channelPool")!!
val unionId = AttributeKey.newInstance<String>("uuid")

val errorResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(500))

const val defaultReadTimeout: Long = 60
const val defaultWriteTimeout: Long = 40

const val maxSocketNum = 50