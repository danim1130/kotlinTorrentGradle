package torrent

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import peer.InfoHash
import peer.PeerTcpChannel
import torrent.message.NewPeerChannel
import util.SHA1
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

class TorrentManager(){

    private val torrents = mutableMapOf<SHA1, Torrent>()

    init {
        launch(CommonPool){startListening()}
    }

    private suspend fun startListening(){
        val server = AsynchronousServerSocketChannel.open()
        server.bind(InetSocketAddress(6881))

        val socket = server.aAccept()
        val channel = PeerTcpChannel(socket)
        launch(CommonPool){
            val message = channel.receiveChannel.receive() as? InfoHash ?: throw AssertionError("wrong message received")
            if (torrents.containsKey(message.hash)){
                torrents.getValue(message.hash).channel.send(NewPeerChannel(channel))
            } else {
                channel.close()
            }
        }
    }

    fun addTorrent(torrent: Torrent){
        torrents.put(torrent.data.infoHash, torrent)
    }



}