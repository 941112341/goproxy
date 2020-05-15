package config

import util.GetValue
import util.GetValueDefault

data class Configuration(val port:Int, val ssl: Boolean, val sslFile: String?, val sslPassword: String?)

lateinit var GlobalConfig: Configuration

fun Init(base:String = "config"):Int {
    val config = util.NewConfig(base)
    return if (config == null) -1 else {
        GlobalConfig = Configuration(
                port = config.GetValueDefault("port", "443").toInt(),
                ssl = config.GetValueDefault("ssl", "true").toBoolean(),
                sslFile = config.GetValue("sslFile"),
                sslPassword = config.GetValue("sslPassword")
        )
        0
    }
}
