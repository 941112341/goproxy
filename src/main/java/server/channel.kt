package server

import gen.Message
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoop
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


object ChannelManager {

    val nameChannelMap = mutableMapOf<String, SocketChannel>()


}

class SocketChannelPool(private val channelQueue:ConcurrentLinkedQueue<SocketChannel> = ConcurrentLinkedQueue<SocketChannel>(),
                        private val uri:String)
    :Queue<SocketChannel> by channelQueue {
    val bootstarp: Bootstrap
    val group = NioEventLoopGroup()
    init {
        bootstarp = Bootstrap()
        bootstarp.group(group).channel(NioSocketChannel::class.java)
        bootstarp.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel?) {
                val pipeline = ch!!.pipeline()
                pipeline.addLast(ProtobufVarint32FrameDecoder())
                pipeline.addLast(ProtobufDecoder(Message.Response.getDefaultInstance()))
                pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                pipeline.addLast(ProtobufEncoder())


            }
        })
    }

    fun getAndCache() {

    }
}

class ProtoClient: SimpleChannelInboundHandler<Message.Response>() {
    override fun messageReceived(ctx: ChannelHandlerContext?, msg: Message.Response?) {
        val channel = ctx!!.channel()
        val rc = channel.attr(realClient).get()

        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, )
    }

}