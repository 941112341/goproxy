import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.ZooKeeper
import org.junit.Test
import server.Discover
import server.parentPath


class TestDiscover {

    @Test
    fun TestDiscoverInit() {
        config.init()
        Discover.init()
        Thread.sleep(6000000)
//        Discover.close()
    }


    @Test
    fun a() {
//        val zk = ZooKeeper("192.168.0.109:2181", 1000, null)
//        val result = zk.exists(server.parentPath, false)
//        println(result)
        val client = CuratorFrameworkFactory.builder().connectString("192.168.0.109:2181")
                .retryPolicy(ExponentialBackoffRetry(100, 1))
                .sessionTimeoutMs(3000).build()
        client.start()
        val isStart = client.isStarted
        print(isStart)
        client.children.forPath(server.parentPath).forEach { print(it)}
    }
}