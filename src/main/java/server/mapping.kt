package server

import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.eq
import me.liuwj.ktorm.dsl.from
import me.liuwj.ktorm.dsl.select
import me.liuwj.ktorm.dsl.where
import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.int
import me.liuwj.ktorm.schema.varchar
import org.slf4j.LoggerFactory

object Register : Table<Nothing>("register") {
    val id by int("id").primaryKey()
    val name by varchar("name")
    val type by int("type")
    val deleted by int("deleted")
    val url by varchar("url")
}

val log = LoggerFactory.getLogger(Database.javaClass)

// 暂时不支持正则路径
var map = mutableMapOf<String, String>()
lateinit var database: Database
var lastRefreshTime:Long = 0

fun initDatabase() {
    database = Database.connect(config.getValue("database.url"), config.getValueDefault("database.driver", "com.mysql.jdbc.Driver"))
    resetUrlMapping()
}

fun resetUrlMapping() {
    val m = mutableMapOf<String, String>()
    val query = database.from(Register).select(Register.columns).where { Register.deleted eq 0 }.forEach {
        val url = it.getString(Register.url.name)!!
        val name = it.getString(Register.name.name)!!
        val oldValue = m.put(url, name)
        if (oldValue != null) {
            log.warn("url {}, old value {}, new value", url, oldValue, name)
        }
    }
    map = m
    lastRefreshTime = System.currentTimeMillis()
}

fun pass(since:Long):Boolean {
    return System.currentTimeMillis() - lastRefreshTime > since
}

fun queryName(url: String): String {
    val name = map[url]
    if (name == null && pass(3000)) {
        resetUrlMapping()
    }
    return map.getOrDefault(url, "/")
}