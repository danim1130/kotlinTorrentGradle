package torrent

import peer.Peer
import util.SHA1
import kotlin.math.min

const val BlockLength = 1 shl 14;

class Piece(val index: Int, private val length: Int, private val pieceHash: SHA1) {

    private val blockCount = (length + BlockLength - 1) / BlockLength
    val blockList = 0.until(blockCount).map { Block(it) }

    var data: ByteArray? = null
        private set

    var isCompleted = false
        private set

    fun onBlockReceived(block: PieceBlock) {
        if (isCompleted) return

        synchronized(this) {
            blockList[block.baseOffset/ BlockLength].data = block.data
            if (blockList.all { it.data != null }) {
                if (data == null || data!!.size != length) data = ByteArray(length)
                blockList.forEachIndexed { index, block ->
                    System.arraycopy(block.data, 0, data, index * BlockLength, block.data!!.size)
                    block.data = null
                }
                val hash = SHA1.getHash(data!!)
                if (hash != pieceHash) {
                    clearData()
                } else {
                    isCompleted = true
                }
            }
        }
    }

    fun getNextEmptyBlockForPeer(peer: Peer): RequestBlock? = synchronized(this) {
        blockList.firstOrNull { it.data == null && it.peersDownloading.size == 0}?.let{
            it.peersDownloading.add(peer)
            return RequestBlock(index,
                    it.blockIndex * BlockLength,
                    min(this.length - it.blockIndex * BlockLength, BlockLength))
        }

        null
    }

    fun getNextRarestBlockForPeer(peer: Peer): RequestBlock? = synchronized(this) {
        blockList.filter { it.data == null }.filterNot { it.peersDownloading.contains(peer) }
                .minBy { it.peersDownloading.size }
                ?.let {
                    it.peersDownloading.add(peer);
                    return RequestBlock(index,
                            it.blockIndex * BlockLength,
                            min(this.length - it.blockIndex * BlockLength, BlockLength))
        }

        null
    }

    fun clearData() = synchronized(this) {
        data = null
        blockList.forEach { it.data = null; }
    }
}

class Block(val blockIndex: Int){
    var data : ByteArray? = null
    val peersDownloading = mutableListOf<Peer>()
}

data class RequestBlock(val pieceIndex: Int, val baseOffset: Int, val length: Int)
class PieceBlock(val pieceIndex: Int, val baseOffset: Int, val data: ByteArray)