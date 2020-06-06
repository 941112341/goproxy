package server

import config.getValue
import config.getValueDefault
import org.apache.curator.framework.CuratorFrameworkFactory.*
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

const val sessionTimeout = 3000
const val parentPath = "/service_discover"
const val zkServer = "zkServer"

object Discover {

    private val log = LoggerFactory.getLogger(javaClass)
    private val discoverMap = ConcurrentHashMap<String, Source>()
    private val framework = builder().connectString(getValue(zkServer))
            .retryPolicy(ExponentialBackoffRetry(1000, 3))
            .sessionTimeoutMs(sessionTimeout).build()
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

    private fun addMap(namespace: String, source: String) {
        if (namespace == parentPath) {
            return
        }
        val src = Source(source)
        discoverMap[realNamespace(namespace)] = src
    }


    private fun removeMap(path: String) {
        discoverMap.remove(realNamespace(path))
    }

    private fun replaceMap(path: String, source: String) {
        discoverMap.replace(realNamespace(path), Source(source))
    }

    fun getSource(path: String):Source? {
        return discoverMap[realNamespace(path)]
    }

}

inline class Source(val src: String) {
    fun toPair(): Pair<String, Int> {
        val array = src.split(":")
        return Pair(array.getOrElse(0) {"127.0.0.1"}, array.getOrElse(1) {"8080"} .toInt())
    }
}

