package server

import org.junit.Test

class MappingKtTest {

    @Test
    fun testResetUrlMapping() {
        val x = """/hello/([^/]*)/ttt""".toRegex()
        x.findAll("/hello/gogogo/ttt").forEach { it.groupValues.forEach{ println(it)} }
    }
}