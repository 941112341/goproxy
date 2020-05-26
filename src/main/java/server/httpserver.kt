package server

import config.GlobalConfig
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.security.*
import java.security.cert.CertificateException
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory


lateinit var server:HttpServer

fun Init() {
    try {
        server = HttpServer(GlobalConfig.port)
        server.run()
    } catch (ex: java.lang.Exception) {
        ex.printStackTrace()
    } finally {
        server.shutDown()
    }
}

class HttpServer(private val port: Int) {
    private val masterThreadPool:NioEventLoopGroup
    private val workerThreadPool:NioEventLoopGroup
    private val server:ServerBootstrap
    private val log = LoggerFactory.getLogger(javaClass)
    val sslContext by lazy {
        initSSLContext()
    }

    init {
        val server = ServerBootstrap()
        masterThreadPool = NioEventLoopGroup().also { workerThreadPool = NioEventLoopGroup() }
        server.group(masterThreadPool, workerThreadPool).channel(NioServerSocketChannel::class.java)
        server.option(ChannelOption.SO_KEEPALIVE, true)
        server.childHandler(object: ChannelInitializer<SocketChannel>() {
            @Throws(Exception::class)
            override fun initChannel(socketChannel: SocketChannel) {
                val pipeline = socketChannel.pipeline()
                pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
                if (GlobalConfig.ssl) {
                    val sslEngine: SSLEngine = sslContext.createSSLEngine()
                    sslEngine.useClientMode = false
                    pipeline.addLast("tls", SslHandler(sslEngine))
                }
                pipeline.addLast("https", HttpServerCodec())
                pipeline.addLast("aggregator", HttpObjectAggregator(1024 * 1024))
                pipeline.addLast("core", CoreServer())
            }
        })
        this.server = server
        ChannelManager.debug()
    }

    fun run() {
        val future = server.bind( this.port).sync()
        if (!future.isSuccess) {
            throw Exception(future.cause().message)
        } else {
            log.debug("success bind port {}", this.port)
        }
        future.channel().closeFuture().sync()
    }

    fun shutDown() {
        masterThreadPool.shutdownGracefully().also { workerThreadPool.shutdownGracefully() }
        ChannelManager.close()
    }

}


private fun initSSLContext(): SSLContext {
    try {
        val pwd: CharArray = GlobalConfig.sslPassword!!.toCharArray()
        val ks = KeyStore.getInstance("JKS")
        val input: InputStream = HttpServer::class.java.classLoader.getResourceAsStream(GlobalConfig.sslFile!!)!!
        ks.load(input, pwd)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, pwd)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)
        return sslContext
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}

