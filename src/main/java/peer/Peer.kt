package peer

import javafx.application.Application.launch
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import torrent.PieceBlock
import torrent.RequestBlock
import torrent.Torrent
import torrent.message.*;
import util.BitField
import kotlin.properties.Delegates

class Peer(private val messageChannel : PeerTcpChannel, val torrent : Torrent){

    val haveBitfield = BitField(torrent.data.info.pieces.size)

    val remoteAddress = messageChannel.remoteAddress!!
    lateinit var peerId : String;
    lateinit var extensions : BitField;

    var lastMessageTime : Long = -1;
    var handshakeComplete = false;

    val requestedBlocks = mutableSetOf<RequestBlock>()

    var isInterestedInMe by Delegates.observable(false){ _, oldValue, newValue ->
        if (oldValue != newValue) launch(CommonPool){torrent.channel.send(PeerInterest(this@Peer, newValue))}
    }

    var isChokingMe = true
        set(value) {
            val oldValue = field;
            field = value;
            if (oldValue != value) launch(CommonPool){torrent.channel.send(PeerChoke(this@Peer, value))}
        }

    var amInterested = false
        set(value) {
            if (field != value) {
                field = value
                launch(CommonPool){messageChannel.senderChannel.send(if (value) InterestedMessage() else NotInterestedMessage())}
            }
        }

    var amChoking by Delegates.observable(true){_, oldValue, newValue ->
        if (oldValue != newValue) launch(CommonPool) {
            messageChannel.senderChannel.send(if (newValue) ChokeMessage() else UnchokeMessage())
        }
    }

    init {
        launch(CommonPool){
            completeConnecting()
            startCommunicationLoop()
        }
    }

    private suspend fun completeConnecting(){
        val extensionsField = BitField(64)
        messageChannel.senderChannel.send(HandshakeMessage("BitTorrent protocol", extensionsField, torrent.data.infoHash, torrent.peerId))

        val handshakeMessage = messageChannel.receiveChannel.receive() as? HandshakeMessage ?: //Might be infohash message
                messageChannel.receiveChannel.receive() as? HandshakeMessage ?:
                throw AssertionError("First received message not handshake")
        if (handshakeMessage.infoHash != torrent.data.infoHash) throw AssertionError("Received infohash does not match")

        peerId = handshakeMessage.peerId
        extensions = handshakeMessage.extensions

        messageChannel.senderChannel.send(BitfieldMessage(torrent.pieceSet))
        handshakeComplete = true
    }

    suspend fun startCommunicationLoop(){
        for (message in messageChannel.receiveChannel){
            lastMessageTime = System.nanoTime()
            when(message){
                is ChokeMessage -> isChokingMe = true
                is UnchokeMessage -> isChokingMe = false
                is InterestedMessage -> isInterestedInMe = true
                is NotInterestedMessage -> isInterestedInMe = false

                is HaveMessage ->   {haveBitfield.setBit(message.pieceIndex)
                                    torrent.channel.send(PeerHasIndex(this, message.pieceIndex))}
                is BitfieldMessage -> { haveBitfield.or(message.bitfield)
                                        torrent.channel.send(PeerBitfield(this, message.bitfield))}
                is RequestMessage -> torrent.channel.send(PeerBlockRequest(this, message.block))
                is PieceMessage -> {
                    requestedBlocks.remove(RequestBlock(message.block.pieceIndex, message.block.baseOffset, message.block.data.size))
                    torrent.channel.send(PeerPiece(this, message.block))
                }

                is CancelMessage -> torrent.channel.send(PeerCancel(this, message.block))
            }
        }
        torrent.channel.send(PeerDisconnected(this));
    }

    fun sendPiece(piece: PieceBlock) = launch(CommonPool){
        messageChannel.senderChannel.send(PieceMessage(piece))
    }

    fun sendHavePiece(pieceIndex : Int) = launch(CommonPool){
        messageChannel.senderChannel.send(HaveMessage(pieceIndex))
    }

    fun sendRequest(requestBlock: RequestBlock) {
        synchronized(requestedBlocks){
            if (requestedBlocks.contains(requestBlock)) return
            requestedBlocks.add(requestBlock)
        }
        launch(CommonPool){messageChannel.senderChannel.send(RequestMessage(requestBlock))}
    }

    fun sendCancelBlock(requestBlock: RequestBlock) = launch(CommonPool){
        messageChannel.senderChannel.send(CancelMessage(requestBlock))
    }
}