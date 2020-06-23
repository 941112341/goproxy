package server

import client.ProtobufClient
import org.junit.Test


class TestClient {
    @Test
    fun getContent() {
        ProtobufClient.test()
        Thread.sleep(10000)
    }


    @Test
    fun split() {
        val s = "/hello/world".split("/").filter { it != "" }
        val ss = "hello/world".split("/")
        println(s.size == ss.size)
    }
}