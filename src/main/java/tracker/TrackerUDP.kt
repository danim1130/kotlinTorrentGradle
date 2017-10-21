package tracker

import java.io.IOException
import java.util.HashSet
import java.util.Random
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel


internal class TrackerUDP(listener: TrackerManager, address: String) : Tracker(listener) {  //TODO: Completely rework udp to use a single port, cache connection id

    private val uri: URI = URI(address)

    private val lastConnectionTime = java.lang.Long.MIN_VALUE
    private var downloadedAtStart: Long = 0

    override suspend fun connect(information: TrackerTorrentInformation) {
        DatagramChannel.open().use { channel ->
        try {
                channel!!.configureBlocking(true)
                channel.connect(InetSocketAddress(uri.getHost(), uri.getPort()))
                channel.socket().soTimeout = 15

                val random = Random()
                var transactionId = random.nextInt()

                val buffer = ByteBuffer.allocate(1024 * 16)
                buffer.putLong(0x41727101980L) //Connection ID
                buffer.putInt(0) //Action: connect
                buffer.putInt(transactionId)
                buffer.flip()
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }

                buffer.clear()
                channel.read(buffer)

                buffer.flip()
                if (buffer.int != 0) { //Action type : connect
                    throw IOException("Wrong action id!")
                }
                if (buffer.int != transactionId) {
                    throw IOException("Wrong transaction id!")
                }
                val connectionId = buffer.getLong()

                val event: Int
                when (state) {
                    TrackerState.COMPLETING -> {
                        event = 1
                        downloadedAtStart = information.downloaded
                    }
                    TrackerState.STARTING -> event = 2
                    TrackerState.STOPPING -> event = 3
                    else -> event = 0
                }

                transactionId = random.nextInt()
                buffer.clear()
                buffer.putLong(connectionId)
                        .putInt(1) //Action : announce
                        .putInt(transactionId)
                buffer.put(information.infoHash)
                buffer.put(information.peerId.toByteArray())
                        .putLong(information.downloaded - downloadedAtStart)
                        .putLong(information.left)
                        .putLong(information.uploaded)

                buffer.putInt(event)
                        .putInt(0) //IP-address
                        .putInt(information.key)
                        .putInt(information.peerWanted)
                        .putShort(information.port)
                buffer.flip()
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }

                buffer.clear()
                channel.read(buffer)
                buffer.flip()

                val action = buffer.getInt()
                if (action == 3) {
                    buffer.getInt()
                    val error = String(buffer.array(), buffer.position(), buffer.remaining())
                    throw IOException(error)
                } else if (action != 1) {
                    throw IOException("Wrong action type received")
                }

                if (buffer.getInt() !== transactionId) {
                    throw IOException("Wrong transaction id received")
                }
                this.interval = buffer.getInt().toLong()
                this.leecherCount = buffer.getInt().toLong()
                this.seederCount = buffer.getInt().toLong()

                val addressList = HashSet<SocketAddress>()
                val ipAddress = ByteArray(4)
                while (buffer.hasRemaining()) {
                    buffer.get(ipAddress)
                    val port = buffer.getShort()
                    val address = InetSocketAddress(InetAddress.getByAddress(ipAddress), port.toInt() and 0x0000_ffff)
                    addressList.add(address)
                }

                lastResponseTime = System.nanoTime()
                when (state) {
                    TrackerState.COMPLETING, TrackerState.STARTING -> state = TrackerState.STARTED
                    TrackerState.STOPPING -> state = TrackerState.STOPPED
                }

                println("Tracker response received from: $uri")
                this.listener.onResponseReceived(addressList)
            } catch (e: Exception) {
                println("Exception during UDP tracker connect : $e, address $uri")
                state = TrackerState.FAILED
                failureReason = e.message
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as TrackerUDP?

        return uri == that!!.uri

    }

    override fun hashCode(): Int = uri.hashCode()
}