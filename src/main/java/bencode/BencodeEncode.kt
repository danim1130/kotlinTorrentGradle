package bencode

import java.io.OutputStream
import java.nio.charset.Charset

fun (Any).bEncode(outputStream: OutputStream) = encodeObj(this, outputStream)

fun encodeObj(obj : Any, outputStream: OutputStream){
    when(obj){
        is Long -> encodeInt(obj, outputStream)
        is String -> encodeString(obj, outputStream)
        is List<*> -> encodeList(obj, outputStream)
        is Map<*,*> -> encodeMap(obj, outputStream)
        else -> throw AssertionError("Can't encode this object")
    }
}

private fun encodeInt(int: Long, outputStream: OutputStream){
    outputStream.write('i'.toInt())
    outputStream.write(int.toString().toByteArray())
    outputStream.write('e'.toInt())
}

private fun encodeString(string: String, outputStream: OutputStream){
    outputStream.write(string.length.toString().toByteArray(Charset.forName("ISO-8859-1")))
    outputStream.write(':'.toInt())
    outputStream.write(string.toByteArray(Charset.forName("ISO-8859-1")))
}

private fun encodeList(list : List<*>, outputStream: OutputStream){
    outputStream.write('l'.toInt())
    for (obj in list){
        encodeObj(obj!!, outputStream)
    }
    outputStream.write('e'.toInt())
}

private fun encodeMap(map : Map<*, *>, outputStream: OutputStream){
    outputStream.write('d'.toInt())
    for ((key, value) in map.mapKeys { it.key as String }.toSortedMap()){
        encodeString(key, outputStream)
        encodeObj(value!!, outputStream)
    }
    outputStream.write('e'.toInt())
}