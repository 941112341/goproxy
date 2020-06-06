package config

import checkers.units.quals.C
import util.Config
import util.GetValue
import util.GetValueDefault

data class Configuration(val port:Int, val ssl: Boolean, val sslFile: String?, val sslPassword: String?)

lateinit var GlobalConfig: Configuration
lateinit var Config: Config

fun init(base:String = "config") {
    Config = util.NewConfig(base)!!
        GlobalConfig = Configuration(
                port = Config.GetValueDefault("port", "443").toInt(),
                ssl = Config.GetValueDefault("ssl", "true").toBoolean(),
                sslFile = Config.GetValue("sslFile"),
                sslPassword = Config.GetValue("sslPassword")
        )

}

fun getValueDefault(key: String, value: String): String {
    return Config.GetValueDefault(key, value)
}

fun getValue(key:String):String {
    return Config.GetValue(key)!!
}