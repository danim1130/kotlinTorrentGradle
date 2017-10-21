package peer

import torrent.PieceBlock
import torrent.RequestBlock
import util.BitField
import util.SHA1
import java.nio.ByteBuffer
import java.util.*

sealed class PeerMessage(){
    abstract val size : Int
    abstract fun writeToByteBuffer(buffer: ByteBuffer) : ByteBuffer
}

class InfoHash(val hash: SHA1) : PeerMessage() {
    override val size: Int
        get() = throw NotImplementedError("Message methods not defined for peer.InfoHash")

    override fun writeToByteBuffer(buffer: ByteBuffer) : ByteBuffer {
        throw NotImplementedError("Message methods not defined for peer.InfoHash")
    }
}

class UnidentifiedMessage(val messageId: Int, val payloadLength: Int) : PeerMessage(){
    override val size: Int
        get() = throw NotImplementedError("Message methods not defined for peer.UnidentifiedMessage")

    override fun writeToByteBuffer(buffer: ByteBuffer) : ByteBuffer {
        throw NotImplementedError("Message methods not defined for peer.UnidentifiedMessage")
    }
}

class HandshakeMessage(val protocol : String, val extensions : BitField, val infoHash: SHA1, val peerId : String) : PeerMessage() {
    override val size = 49 + protocol.length
    override fun writeToByteBuffer(buffer: ByteBuffer): ByteBuffer = buffer.put(size.toByte()).put(protocol.toByteArray())
            .put(extensions.array).put(infoHash.byteValue).put(peerId.toByteArray())
}

class KeepAliveMessage : PeerMessage() {
    override val size = 4
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4)
}
class ChokeMessage : PeerMessage() {
    override val size = 5
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(0)
}
class UnchokeMessage : PeerMessage() {
    override val size = 5
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(1)
}
class InterestedMessage : PeerMessage() {
    override val size = 5
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(2)
}
class NotInterestedMessage : PeerMessage() {
    override val size = 5
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(3)
}

class HaveMessage(val pieceIndex : Int) : PeerMessage() {
    override val size = 9
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(4).putInt(pieceIndex)
}
class BitfieldMessage(val bitfield: BitField) : PeerMessage() {
    override val size = 5 + (bitfield.array.size)
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(5).put(bitfield.array)
}
class RequestMessage(val block: RequestBlock) : PeerMessage() {
    override val size = 17
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(6).putInt(block.pieceIndex)
            .putInt(block.baseOffset).putInt(block.length)
}
class PieceMessage(val block: PieceBlock) : PeerMessage() {
    override val size = 13 + block.data.size
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(7).putInt(block.pieceIndex)
            .putInt(block.baseOffset).put(block.data)
}
class CancelMessage(val block: RequestBlock) : PeerMessage() {
    override val size = 17
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(6).putInt(block.pieceIndex)
            .putInt(block.baseOffset).putInt(block.length)
}

class PortMessage(val port: Short) : PeerMessage() {
    override val size = 7
    override fun writeToByteBuffer(buffer: ByteBuffer) = buffer.putInt(size - 4).put(9).putShort(port)
}