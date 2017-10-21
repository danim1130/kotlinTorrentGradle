package torrent

import bencode.bEncode
import util.SHA1
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.*

sealed class InfoData(base: Map<String, Any>){
    val name : String by base
    val pieceLength : Long = base["piece length"] as Long
    val pieces = (base.getValue("pieces") as String).toByteArray(Charset.forName("ISO-8859-1"))
                        .asIterable().chunked(20) {it.toByteArray()}
    val private by lazy { base.getOrDefault("private", 0) == 1 }
}

class FileData(base: Map<String, Any>){
    val name : String by base
    val length : Long by base
}

class SingleFileInfoData(base: Map<String, Any>) : InfoData(base){
    val file = FileData(base)
}

class MultiFileInfoData(base: Map<String, Any>) : InfoData(base){
    val files : List<FileData> = (base.getValue("files") as List<Map<String, Any>>).map { fileMap ->
        val baseMap = mutableMapOf<String, Any>()
        baseMap["length"] = fileMap.getValue("length")
        baseMap["name"] = (fileMap.getValue("path") as List<String>).reduce { acc: String, s: String ->
            acc.plus("/$s")
        }
        FileData(baseMap)
    }
}

class TorrentData(map: Map<String, Any>){
    val info : InfoData
    val infoHash : SHA1
    val announce : String by map
    val announceList : List<List<String>>
    val creationDate : Date? = (map.get("creationDate") as Long?)?.let { Date(it) }
    val comment : String? by map
    val createdBy : String? by map
    val encoding : String? by map

    init {
        val infoMap = map.getValue("info") as Map<String, Any>
        info = if (infoMap.containsKey("files")) MultiFileInfoData(infoMap) else SingleFileInfoData(infoMap)

        announceList = map["announce-list"] as List<List<String>>

        val byteArrayOutputStream = ByteArrayOutputStream()
        infoMap.bEncode(byteArrayOutputStream)
        infoHash = SHA1.getHash(byteArrayOutputStream.toByteArray())
    }
}