import bencode.decodeStream
import torrent.TorrentData
import torrent.Torrent
import torrent.TorrentManager
import java.io.FileInputStream

class A(map: Map<String, Any>){
    val test: Int by map
    val first: List<Int> by map
}

fun main(args: Array<String>){
    val inputStream = FileInputStream("ubuntu.torrent")
    val map = inputStream.use { decodeStream(inputStream) } as Map<String, Any>
    val torrentData = TorrentData(map)

    val torrent = Torrent(torrentData)

    val torrentManager = TorrentManager();
    torrentManager.addTorrent(torrent)

    torrent.startTorrent()

    while (true){
        println("Left: ${torrent.left}")
        Thread.sleep(5000)
    }
}