package server

import java.io.Closeable


interface ShortServer:Closeable {

    fun start()
}