package peer

//import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import torrent.PieceBlock
import torrent.RequestBlock
import util.BitField
import util.SHA1
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.AsynchronousSocketChannel


class PeerTcpChannel(private val socketChannel: AsynchronousSocketChannel){

    val receiveChannel = produce(CommonPool, 10){
        try {
            val buffer = ByteBuffer.allocate(17 * 1024) //TODO: check if overhead is neccessary
            buffer.order(ByteOrder.BIG_ENDIAN)
            buffer.flip()

            //Handle handshaking
            socketChannel.waitForData(buffer, 1)
            val protocolLength = (buffer.get().toInt() and 255)
            socketChannel.waitForData(buffer, protocolLength + 28)
            val protocol = buffer.getString(protocolLength)
            val extensions = buffer.getBitField(8)
            val infoHash = SHA1.fromByteBuffer(buffer);

            send(InfoHash(infoHash))

            socketChannel.waitForData(buffer, 20)
            val peerId = buffer.getString(20)

            send(HandshakeMessage(protocol, extensions, infoHash, peerId))

            //Handshake finished, start message loop

            while (true) {
                socketChannel.waitForData(buffer, 4)
                val messageLength = buffer.int;
                if (messageLength == 0) {
                    send(KeepAliveMessage())
                    continue
                }

                socketChannel.waitForData(buffer, 1)
                val messageId = buffer.get().toInt()

                val payloadLength = messageLength - 1;
                socketChannel.waitForData(buffer, payloadLength)

                send(when (messageId) {
                    0 -> ChokeMessage()
                    1 -> UnchokeMessage()
                    2 -> InterestedMessage()
                    3 -> NotInterestedMessage()

                    4 -> HaveMessage(buffer.int)
                    5 -> BitfieldMessage(buffer.getBitField(payloadLength))
                    6 -> RequestMessage(RequestBlock(pieceIndex = buffer.int, baseOffset = buffer.int, length = buffer.int))
                    7 -> PieceMessage(PieceBlock(pieceIndex = buffer.int, baseOffset = buffer.int,
                            data = ByteArray(payloadLength - 8).apply { buffer.get(this) }))
                    8 -> CancelMessage(RequestBlock(pieceIndex = buffer.int, baseOffset = buffer.int, length = buffer.int))

                    9 -> PortMessage(port = buffer.short)
                    else -> {
                        buffer.stepForward(payloadLength)
                        UnidentifiedMessage(messageId, payloadLength)
                    }
                })
            }
        } catch (e: Exception){
            close(e)
        }
    }

    val senderChannel = actor<PeerMessage>(CommonPool, 10){
        try {
            val buffer = ByteBuffer.allocate(17 * 1024)
            buffer.order(ByteOrder.BIG_ENDIAN)

            val handshake = receive() as? HandshakeMessage ?: throw AssertionError("First message received is not Handshake!")
            buffer.put((handshake.protocol.length).toByte())
            buffer.put(handshake.protocol.toByteArray())
            buffer.put(handshake.extensions.array)
            buffer.put(handshake.infoHash.byteValue)
            buffer.put(handshake.peerId.toByteArray())

            var nextMessage: PeerMessage? = poll()
            while (true) {
                if (nextMessage == null && buffer.position() == 0) { //block if we don't have anything to send, poll otherwise
                    nextMessage = receive()
                } else {
                    nextMessage = poll()
                }
                while (nextMessage != null && nextMessage.size < buffer.remaining()) {
                    nextMessage.writeToByteBuffer(buffer)
                    nextMessage = poll()
                }
                buffer.flip()
                socketChannel.aWrite(buffer)
                buffer.compact()
            }
        } catch (e: Exception){
            socketChannel.close()
        }
        while (true) receive() //Keep que empty
    }

    fun close(){
        socketChannel.close()
    }

    val remoteAddress = socketChannel.remoteAddress
}

private fun ByteBuffer.getBitField(length : Int): BitField {
    val array = ByteArray(length)
    this.get(array)
    return BitField(8 * length, array)
}

private fun Buffer.stepForward(num: Int){
    this.position(this.position() + num)
}

private fun ByteBuffer.getString(length: Int) : String{
    val arr = ByteArray(length)
    this.get(arr)
    return String(arr)
}

private suspend fun AsynchronousSocketChannel.waitForData(buffer: ByteBuffer, bytes: Int){
    buffer.compact()
    var remaining = bytes - buffer.position()
    while (remaining > 0){
        val received = this.aRead(buffer)
        if (received == -1){
            throw Exception("Channel closed")
        }
        remaining -= received
    }
    buffer.flip()
}