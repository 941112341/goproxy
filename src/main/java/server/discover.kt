package server

import config.getValueDefault
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.CuratorFrameworkFactory.*
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

const val sessionTimeout = 3000
const val connectTimeout = 3000
const val parentPath = "/service_discover"
const val zkServer = "zkServer"

object Discover {

    private val log = LoggerFactory.getLogger(javaClass)
    private val discoverMap = ConcurrentHashMap<String, Source>()
    val framework = builder().connectString(getValueDefault(zkServer, "192.168.0.109:2181"))
            .retryPolicy(ExponentialBackoffRetry(100, 1))
            .sessionTimeoutMs(3000).build()
    private val cache = CuratorCache.build(framework, parentPath)

    fun init() {
        framework.start()

        val listen = CuratorCacheListener.builder().forCreates {
            log.info("add path {}, value {}", it.path, it.data)
            addMap(it.path, String(it.data))
        }.forDeletes {
            log.info("add path {}, value {}", it.path, it.data)
            removeMap(it.path)
        }.forChanges { oldNode, node ->
            log.info("old node {}, new node {}", oldNode, node)
            replaceMap(oldNode.path, String(node.data))
        }.build()
        cache.listenable().addListener(listen)
        cache.start()
    }

    fun close() {
        framework.close()
        cache.close()
    }

    private fun addMap(path: String, source: String) {
        if (path == parentPath) {
            return
        }
        val src = Source(source)
        discoverMap[toUri(path)] = src
    }

    private fun removeMap(path: String) {
        discoverMap.remove(toUri(path))
    }

    private fun replaceMap(path: String, source: String) {
        discoverMap.replace(toUri(path), Source(source))
    }

    fun getSource(path: String):Source? {
        return discoverMap[toUri(path)]
    }

    private fun toUri(path: String):String {
        if (path.startsWith(parentPath)) {
            return path.substring(parentPath.length)
        }
        if (path.startsWith("/")) {
            return path
        }
        return "/$path"
    }
}

inline class Source(val src: String) {
    fun toPair(): Pair<String, Int> {
        val array = src.split(":")
        return Pair(array.getOrElse(0) {"127.0.0.1"}, array.getOrElse(1) {"8080"} .toInt())
    }
}

