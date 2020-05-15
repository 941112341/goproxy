package gen



fun Message.Context.getValue(key:String):String? {
    var ctx = this

    do {
        val value = ctx.mapsMap[key]
        if (value != null) {
            return value
        }
        ctx = ctx.ctx
    } while (ctx != Message.Context.getDefaultInstance())

    return null
}
