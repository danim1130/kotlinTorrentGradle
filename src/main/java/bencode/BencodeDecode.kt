package bencode

import java.io.InputStream

//TODO: should this use try-with-resource
fun decodeStream(bEncodedStream: InputStream): Any {
    val stream = if (bEncodedStream.markSupported()) bEncodedStream else bEncodedStream.buffered(1)
    return when (stream.peekCharacter()){
        'i' -> decodeInt(stream)
        'd' -> decodeMap(stream)
        'l' -> decodeList(stream)
        in '0'..'9' -> decodeString(stream)
        else -> throw BencodeException("Illegal character in stream")
    }
}

private fun InputStream.readChar(): Char{
    val char = this.read()
    if (char == -1)
        throw BencodeException("InputStream overrun")
    return char.toChar()
}

private fun InputStream.runUntil(continueBody: (Char) -> Boolean){
    while(continueBody(this.readChar()));
}

private fun InputStream.peekCharacter(): Char{
    if (!this.markSupported())
        throw BencodeException("Mark not supported on stream")

    this.mark(1)
    val ret = this.readChar()
    this.reset()
    return ret
}

fun decodeString(stream: InputStream): String {
    var stringLength = 0
    stream.runUntil {
        if (it == '0' && stringLength == 0) throw BencodeException("Illegal character in stream!")
        else if (it in '0'..'9') {stringLength = stringLength * 10 + it.minus('0'); true}
        else if (it == ':') false
        else throw BencodeException("Illegal character in stream")
    }
    if (stringLength == 0){
        return String()
    } else {
        val stringBuilder = StringBuilder(stringLength)
        stream.runUntil {
            stringBuilder.append(it)
            --stringLength != 0
        }
        return stringBuilder.toString()
    }
}

fun decodeInt(stream: InputStream): Long {
    if (stream.readChar() != 'i')
        throw BencodeException("Illegal character")

    var value = 0L
    val nextChar = stream.readChar()
    if (nextChar == 'e') {
        throw BencodeException("Illegal character")
    }
    val isNegative = nextChar == '-'
    if (!isNegative){
        value = nextChar.minus('0').toLong()
        if (value > 9 || value < 0){
            throw BencodeException("Illegal character in stream")
        }
    }

    stream.runUntil {
        if(it == '0' && value == 0.toLong()) throw BencodeException("Illegal zero in stream")
        else if (it in '0'..'9') {value = value * 10 + it.minus('0'); true}
        else if (it == 'e') false
        else throw BencodeException("Illegal character in stream")
    }
    return if (isNegative) (-1 * value) else value
}

fun decodeList(stream: InputStream): List<Any> {
    if (stream.readChar() != 'l')
        throw BencodeException("Stream overrun")

    val list = mutableListOf<Any>()
    while(true){
        if (stream.peekCharacter() == 'e')
            break
        else
            list.add(decodeStream(stream))
    }
    stream.readChar()
    return list.toList()
}

fun decodeMap(stream: InputStream): Map<String, Any> {
    if (stream.readChar() != 'd')
        throw BencodeException("Stream overrun")

    val map = mutableMapOf<String, Any>()
    while(true){
        if (stream.peekCharacter() == 'e')
            break
        else
            map[decodeString(stream)] = decodeStream(stream)
    }
    stream.readChar()
    return map.toMap()
}