package client

import com.alibaba.fastjson.JSON
import config.getValue
import org.apache.curator.framework.CuratorFrameworkFactory.*
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory
import sun.security.krb5.internal.crypto.Des
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

enum class UrlMappingType(val type:Int) {
    Absolute(0), DynamicPath(1),
}

data class UrlMeta(val url: String, val serviceName: String, val type: UrlMappingType)
data class HostMeta(val serviceName: String, val pairs: List<Pair<String, Int>> = mutableListOf()) {
    var randomIndex = AtomicInteger()

    fun getHost(): Pair<String, Int>? {
        if (pairs.isEmpty()) {
            return null
        }
        var fact: Int
        do {
            val i = randomIndex.get()
            fact = i.inc().rem(pairs.size)
        } while (!randomIndex.compareAndSet(i, fact))

        return pairs[fact]
    }
}

val errUrlMeta = UrlMeta("/error", "error", UrlMappingType.Absolute)

object Discover {

    private val log = LoggerFactory.getLogger(javaClass)
    private val absoluteMap = mutableMapOf<String, UrlMeta>()
    private val regexMap = TreeMap<String, UrlMeta>(kotlin.Comparator { o1, o2 ->
        val oi1 = o1.indexOf("{").coerceAtLeast(0)
        val oi2 = o2.indexOf("{").coerceAtLeast(0)
        oi2 - oi1
    })

    private val serviceMap = mutableMapOf<String, HostMeta>()
    private val framework = builder().connectString(getValue("zkServer"))
            .retryPolicy(ExponentialBackoffRetry(1000, 3))
            .sessionTimeoutMs(3000).build()
    private val cache = CuratorCache.build(framework, "/service_discover")
    private val serviceCache = CuratorCache.build(framework, "/service_host")

    fun init() {
        framework.start()

        val listen = CuratorCacheListener.builder().forCreates {
            log.info("add path {}, value {}", it.path, it.data)
            addMeta(it)
        }.forDeletes {
            log.info("add path {}, value {}", it.path, it.data)
            removeMeta(it)
        }.forChanges { oldNode, node ->
            log.info("old node {}, new node {}", oldNode, node)
            removeMeta(oldNode)
            addMeta(node)
        }.build()

        val serviceListen = CuratorCacheListener.builder().forCreates {
            log.info("add service {}, value {}", it.path, it.data)
            addService(it)
        }.forDeletes {
            log.info("add service {}, value {}", it.path, it.data)
            delService(it)
        }.forChanges { oldNode, node ->
            log.info("old service {}, new service {}", oldNode, node)
            delService(oldNode)
            addService(node)
        }.build()

        cache.listenable().addListener(listen)
        cache.listenable().addListener(serviceListen)
        cache.start()
    }

    private fun removeMeta(it: ChildData) {
        val meta = JSON.parseObject(String(it.data), UrlMeta::class.java)
        when (meta.type) {
            UrlMappingType.DynamicPath -> regexMap.remove(meta.url)
            UrlMappingType.Absolute -> absoluteMap.remove(meta.url)
        }
    }

    private fun addMeta(it: ChildData) {
        val meta = JSON.parseObject(String(it.data), UrlMeta::class.java)
        when (meta.type) {
            UrlMappingType.DynamicPath -> regexMap[meta.url] = meta
            UrlMappingType.Absolute -> absoluteMap[meta.url] = meta
        }
    }

    private fun addService(it: ChildData) {
        val meta = JSON.parseObject(String(it.data), HostMeta::class.java)
        serviceMap[meta.serviceName] = meta
    }

    private fun delService(it: ChildData) {
        val meta = JSON.parseObject(String(it.data), HostMeta::class.java)
        serviceMap.remove(meta.serviceName)
    }


    fun close() {
        framework.close()
        cache.close()
    }


    fun queryMeta(rawUrl: String):DestService {
        val meta = absoluteMap[rawUrl]
        if (meta == null) {
            for ((_, v) in regexMap) {
                val urls = v.url.split("/")
                val rawUrls = rawUrl.split("/")
                if (urls.size != rawUrls.size) {
                    continue
                }
                val maps = mutableMapOf<String,String>()
                for ((i, v) in urls.withIndex()) {
                    val rv = rawUrls[i]
                    if (v.startsWith("{") && v.endsWith("}")) {
                        val paramKey = v.substring(1, v.length - 1)
                        maps[paramKey] = rv
                    } else if (v != rv) {
                        continue
                    }
                }
                return DestService(rawUrl, v, maps)
            }
            return DestService(rawUrl, errUrlMeta)
        }
        return DestService(rawUrl, meta)
    }

    fun queryHost(serviceName:String):HostMeta {
        return serviceMap[serviceName] ?: HostMeta(serviceName)
    }
}

class DestService(val rawUrl:String, val urlMeta: UrlMeta, val otherParameter: Map<String, String> = mutableMapOf())