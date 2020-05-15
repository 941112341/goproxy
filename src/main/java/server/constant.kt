package server

import io.netty.channel.socket.SocketChannel
import io.netty.util.AttributeKey

val rc = "realClient"
val realClient = AttributeKey.newInstance<SocketChannel>(rc)