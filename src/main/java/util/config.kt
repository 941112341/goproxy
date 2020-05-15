package util

import java.util.ResourceBundle

inline class Config(val resourceBundle: ResourceBundle)

fun Config.GetValue(key: String): String? {
    try {
        return this.resourceBundle.getString(key)
    } catch (ex: Exception) {
        ex.printStackTrace()
        return null
    }
}

fun Config.GetValueDefault(key: String, defaultValue: String): String {
    return this.GetValue(key) ?: defaultValue
}

fun NewConfig(filename: String): Config? {
    try {
       return Config(ResourceBundle.getBundle(filename))
    } catch (ex: Exception) {
        ex.printStackTrace()
        return null
    }
}