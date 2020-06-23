package common

import io.netty.channel.Channel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.AttributeKey

val unionId = AttributeKey.newInstance<String>("uuid")!!
val realClient = AttributeKey.newInstance<Channel>("realClient")!!


val errorResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(500))
var notFoundResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(404))


const val serverTimeout:Long = 2

