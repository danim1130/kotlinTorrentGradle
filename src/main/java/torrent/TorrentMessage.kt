package torrent.message

import peer.Peer
import peer.PeerTcpChannel
import torrent.PieceBlock
import torrent.RequestBlock
import util.BitField
import java.net.SocketAddress

sealed class TorrentMessage
class PeerInterest(val peer: Peer, val interested: Boolean): TorrentMessage()
class PeerChoke(val peer: Peer, val choke: Boolean): TorrentMessage()
class PeerBlockRequest(val peer: Peer, val block: RequestBlock): TorrentMessage()
class PeerPiece(val peer: Peer, val block: PieceBlock): TorrentMessage()
class PeerCancel(val peer: Peer, val block: RequestBlock): TorrentMessage()
class PeerBitfield(val peer: Peer, val bitfield: BitField): TorrentMessage()
class PeerHasIndex(val peer: Peer, val index: Int): TorrentMessage()
class PeerDisconnected(val peer: Peer): TorrentMessage()
class NewPeerChannel(val peerChannel: PeerTcpChannel): TorrentMessage()

class PeersDiscovered(val peerAddresses: Set<SocketAddress>): TorrentMessage()