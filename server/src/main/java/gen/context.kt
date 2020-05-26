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


fun Message.Request.isIdle():Boolean {
    return this.ctx == null || this == Message.Request.getDefaultInstance()
}