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

fun Message.Context.wrapValue(key: String, value:String): Message.Context {
    return Message.Context.newBuilder().setCtx(ctx).putMaps(key, value).build()
}

fun Message.Context.wrapValues(map: Map<String, String>):Message.Context {
    return Message.Context.newBuilder().setCtx(ctx).putAllMaps(map).build()
}

fun Message.Request.getValue(key: String):String? {
    return ctx.getValue(key)
}

fun Message.Request.wrapValue(key: String, value:String): Message.Request {
    return Message.Request.newBuilder().setCtx(ctx.wrapValue(key, value)).setParameter(parameter).build()
}

fun Message.Request.wrapValues(map: Map<String, String>): Message.Request {
    return Message.Request.newBuilder().setCtx(ctx.wrapValues(map)).setParameter(parameter).build()
}