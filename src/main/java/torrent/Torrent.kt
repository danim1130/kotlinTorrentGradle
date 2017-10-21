package torrent

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aConnect
import peer.Peer
import peer.PeerTcpChannel
import piece.PieceToFileMapper
import torrent.message.*
import tracker.TrackerManager
import util.BitField
import util.SHA1
import java.net.SocketAddress
import java.nio.channels.AsynchronousSocketChannel
import kotlin.math.min

class Torrent(val data: TorrentData){

    private val MinPeerNumber = 50;
    private val TargetPeerNumber = 100;
    private val MaxPeerNumber = 100;

    private val ParalellBlocksPerPeer : Int = 8
    private var peers = mutableSetOf<Peer>()
    private var foundPeers = mutableSetOf<SocketAddress>()

    private val trackerManager = TrackerManager(this, data.announceList)
    private val pieceMapper = when(data.info){
        is SingleFileInfoData -> PieceToFileMapper(listOf(data.info.file))
        is MultiFileInfoData -> PieceToFileMapper(data.info.files)
    }
    private val torrentLength = when(data.info){
        is SingleFileInfoData -> data.info.file.length
        is MultiFileInfoData -> data.info.files.fold(0.toLong()){acc, fileData -> acc + fileData.length }
    }
    val peerId: String = "-qB33A0-DSh6r9y0E_DH"
    var downloaded: Long = 0
    var uploaded: Long = 0
    var left: Long = torrentLength

    val pieceSet = BitField(data.info.pieces.size)
    private val piecesCount = IntArray(data.info.pieces.size){0}
    private val pieces = data.info.pieces.mapIndexed { index: Int, bytes: ByteArray ->
        val pieceLength = min(torrentLength - index * data.info.pieceLength, data.info.pieceLength).toInt()
        Piece(index, pieceLength, SHA1.fromByteArray(bytes))
    }

    val channel = actor<TorrentMessage>(CommonPool, 10) {
        for (message in this){
            when (message){
                is PeerInterest -> message.peer.amChoking = message.interested.not()
                is PeerChoke -> peerChangedChoke(message.peer, message.choke)
                is PeerBitfield -> peerBitfieldReceived(message.peer, message.bitfield)
                is PeerBlockRequest -> peerBlockRequest(message.peer, message.block)
                is PeerPiece -> peerPieceMessage(message.peer, message.block)
                is PeerCancel -> peerCancelMessage(message.peer, message.block)
                is PeerHasIndex -> peerHasPiece(message.peer, message.index)
                is PeerDisconnected -> removePeer(message.peer)
                is NewPeerChannel -> onNewPeerChannel(message.peerChannel)
                is PeersDiscovered -> onNewPeerAddresses(message.peerAddresses)
            }
        }
    }

    fun startTorrent(){
        trackerManager.startManager()
    }

    private fun peerChangedChoke(peer: Peer, status: Boolean){
        if (!status) {
            fillPeerWithRequests(peer)
        } else {
            peer.requestedBlocks.forEach {
                pieces[it.pieceIndex].blockList[it.baseOffset/ BlockLength].peersDownloading.remove(peer)
            }
            peer.requestedBlocks.clear()
        }
    }

    private fun fillPeerWithRequests(peer: Peer){
        if (peer.requestedBlocks.size >= ParalellBlocksPerPeer) return

        val availablePieceList = pieces.filterIndexed { index, piece ->
            !piece.isCompleted && peer.haveBitfield.getBit(index)
        }.sortedBy { piecesCount[it.index] }

        availablePieceList.forEach { //First search for block no one downloads
            var block = it.getNextEmptyBlockForPeer(peer)
            while (block != null){
                peer.sendRequest(block)
                if (peer.requestedBlocks.size >= ParalellBlocksPerPeer) return
                block = it.getNextEmptyBlockForPeer(peer)
            }
        }

        availablePieceList.shuffled().forEach { //Then search for the rarest block
            var block = it.getNextRarestBlockForPeer(peer)
            while (block != null){
                peer.sendRequest(block!!)
                if (peer.requestedBlocks.size >= ParalellBlocksPerPeer) return
                block = it.getNextRarestBlockForPeer(peer)
            }
        }

        if (peer.requestedBlocks.size == 0)
            peer.amInterested = false
    }

    private fun peerBlockRequest(peer: Peer, block: RequestBlock){
        launch(CommonPool){
            val data = pieceMapper.readBlock(block.pieceIndex * data.info.pieceLength + block.baseOffset, block.length)
            peer.sendPiece(PieceBlock(block.pieceIndex, block.baseOffset, data))
            uploaded += block.length
        }
    }

    private fun peerPieceMessage(peer: Peer, block: PieceBlock) {
        val piece = pieces[block.pieceIndex]
        piece.onBlockReceived(block)

        val peersDownloading = piece.blockList[block.baseOffset/ BlockLength].peersDownloading;
        peersDownloading.forEach { if (it != peer) it.sendCancelBlock(RequestBlock(block.pieceIndex, block.baseOffset, block.data.size)) }
        peersDownloading.clear()
        if (piece.isCompleted && piece.data != null){
            pieceMapper.writePiece(piece.data!!, piece.index * data.info.pieceLength)
            piece.clearData()
            synchronized(peers){
                peers.forEach {it.sendHavePiece(block.pieceIndex)}
            }
        }

        fillPeerWithRequests(peer)

        downloaded += block.data.size
        left = maxOf(left - block.data.size, 0)
    }

    private fun peerCancelMessage(peer: Peer, block: RequestBlock) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun peerBitfieldReceived(peer: Peer, bitfield: BitField) {
        var index = bitfield.nextSetBit(0)
        while (index != -1){
            piecesCount[index]++
            if (!pieces[index].isCompleted) peer.amInterested = true
            index = bitfield.nextSetBit(index + 1)
        }
    }

    private fun peerHasPiece(peer: Peer, pieceIndex: Int) {
        piecesCount[pieceIndex]++
        if (!pieces[pieceIndex].isCompleted) peer.amInterested = true
    }

    private fun onNewPeerAddresses(peerAddresses: Set<SocketAddress>) {
        val connectedPeers = peers.map { it.remoteAddress }
        val unconnectedPeers = peerAddresses.filter { !connectedPeers.contains(it) }
        foundPeers.addAll(unconnectedPeers)
        fillPeersToTarget()
    }

    private fun fillPeersToTarget() : Unit = synchronized(peers){
        val needed = TargetPeerNumber - peers.size
        if (needed <= 0) return
        val has = foundPeers.size
        val canConnectTo = minOf(needed, has)
        if (canConnectTo < needed){
            //trackerManager.retriveMorePeers()
        }

        repeat(canConnectTo) {
            connectOnePeer()
        }
    }

    private fun connectOnePeer() : Unit {
        var socketAddress : SocketAddress? = null
        if (foundPeers.size == 0){
            return;
        }
        socketAddress = foundPeers.first()
        foundPeers.remove(socketAddress!!)

        launch(CommonPool) {
            val channel = AsynchronousSocketChannel.open()
            try {
                channel.aConnect(socketAddress!!)
                this@Torrent.channel.send(NewPeerChannel(PeerTcpChannel(channel)))
            } catch (e: Exception) {

            }
        }
    }

    private fun removePeer(peer: Peer){
        peers.remove(peer)
        if (peers.size < MinPeerNumber){
            fillPeersToTarget()
        }
    }

    private fun onNewPeerChannel(peerChannel: PeerTcpChannel) {
        if (peers.size > MaxPeerNumber){
            peerChannel.close()
        } else {
            peers.add(Peer(peerChannel, this))
        }
    }

}

