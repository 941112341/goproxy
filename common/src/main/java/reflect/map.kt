package reflect

import com.alibaba.fastjson.JSON
import java.lang.reflect.Field


class Reflector(val o:Any) {
    private val clz = o::class.java

    @Throws
    fun set(key: String, value: String) {
        withField(key) {
            val fld = it
            when (fld.type) {
                Int.javaClass -> fld.setInt(o, value.toInt())
                Short.javaClass -> fld.setShort(o, value.toShort())
                Long.javaClass -> fld.setLong(o, value.toLong())
                Boolean.javaClass -> fld.setBoolean(o, value.toBoolean())
                Double.javaClass -> fld.setDouble(o, value.toDouble())
                Float.javaClass -> fld.setFloat(o, value.toFloat())
                Char.javaClass -> fld.setChar(o, value[0])

                else -> setObject(key, value)
            }
        }
    }

    protected fun setObject(key: String, value: String) {
        withField(key) {
            val fldVal = JSON.parseObject(value, it.type)
            it.set(o, fldVal)
        }
    }


    private fun withField(key: String, with: (Field) -> Unit) {
        val fld = clz.getField(key)
        if (fld.trySetAccessible()) {
            with(fld)
        }
    }
}

