import com.alibaba.fastjson.JSON
import gen.Message
import gen.getValue
import gen.isIdle
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import org.slf4j.LoggerFactory

class TestServer {



    fun a() {
        val bootstrap = ServerBootstrap()
        val worker = NioEventLoopGroup()
        val master = NioEventLoopGroup()

        try {
            bootstrap.group(worker, master).channel(NioServerSocketChannel::class.java).childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel?) {
                    val channel = ch!!.pipeline()
                    channel.addLast(ProtobufVarint32FrameDecoder())
                    channel.addLast(ProtobufDecoder(Message.Request.getDefaultInstance()))
                    channel.addLast(ProtobufVarint32LengthFieldPrepender())
                    channel.addLast(ProtobufEncoder())
                    channel.addLast(TestServerHandler())
                }
            })
            val f = bootstrap.bind(8888).sync()
            f.channel().closeFuture().sync()
        } finally {
            worker.shutdownGracefully()
            master.shutdownGracefully()
        }

    }

}

fun main() {
    TestServer().a()
}




class TestServerHandler: ChannelHandlerAdapter() {

    val log = LoggerFactory.getLogger(javaClass)

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val req = msg as Message.Request
        val channel = ctx!!.channel()
        if (req.isIdle()) {
            log.info("receive idle message")
            channel.writeAndFlush(Message.Response.getDefaultInstance())
            return
        }

        log.info("body is {} ", req.parameter)
        log.info("header is {}", req.ctx.getValue("Host"))

        val maps = mapOf(Pair("hello", "world"))
        val s = JSON.toJSONString(maps)
        channel.writeAndFlush(Message.Response.newBuilder().setCtx(
                Message.Context.newBuilder().putAllMaps(mapOf(Pair("http-header", s))).build()
        ).build())
    }
}