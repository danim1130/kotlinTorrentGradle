package tracker

import java.net.InetAddress



enum class TrackerState {
    STARTING, STARTED, STOPPING, STOPPED, COMPLETING, FAILED
}

data class TrackerTorrentInformation (val infoHash: ByteArray, val peerId: String, val port: Short, val downloaded: Long, val uploaded: Long,
                    val left: Long, val isCompact: Boolean, val ip: InetAddress? = null,
                    val peerWanted: Int, val key: Int = 0)

abstract class Tracker(protected var listener: TrackerManager) {

    protected var state = TrackerState.STARTING

    protected var lastResponseTime = java.lang.Long.MIN_VALUE
    var interval = java.lang.Long.MAX_VALUE
        protected set
    protected var minInterval: Long = 0
    var failureReason: String? = null
        protected set
    protected var seederCount: Long = 0
    protected var leecherCount: Long = 0

    suspend fun stopTracker(currentTorrent: TrackerTorrentInformation) {
        if (state === TrackerState.STARTED) {
            state = TrackerState.STOPPING
            connect(currentTorrent)
        }
    }

    suspend fun completeTracker(currentTorrent: TrackerTorrentInformation) {
        if (state === TrackerState.STARTED) {
            state = TrackerState.COMPLETING
            connect(currentTorrent)
        }
    }

    suspend fun sendInformation(currentTorrent: TrackerTorrentInformation) {
        if (state !== TrackerState.FAILED) {
            connect(currentTorrent)
        }
    }

    suspend fun retryConnection(currentTorrent: TrackerTorrentInformation) {
        if (state === TrackerState.FAILED) {
            state = TrackerState.STARTING
            connect(currentTorrent)
        }
    }

    fun hasFailed(): Boolean {
        return state === TrackerState.FAILED
    }

    protected abstract suspend fun connect(information: TrackerTorrentInformation)

    companion object {

        fun fromAddress(listener: TrackerManager, address: String): Tracker {
            if (address.startsWith("http://")) {
                return TrackerHTTP(listener, address)
            } else if (address.startsWith("udp://")) {
                return TrackerUDP(listener, address)
            }

            throw AssertionError("Unsupported tracker type: $address")
        }
    }
}