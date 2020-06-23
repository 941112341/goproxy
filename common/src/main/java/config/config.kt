package config

import java.io.FileInputStream

object Configs {


}


interface Config {
    fun read(o: Any)
}

abstract class PathConfig(filename: String) : Config {

    private val realPath = getPath()
    protected abstract fun getPath():String

    override fun read(o: Any) {
        val p = java.util.Properties()
        val file = FileInputStream(realPath)
        p.load(file)

    }
}


class PropertyConfig(filename: String):Config {
    override fun read(o: Any) {

    }
}
