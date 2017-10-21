package tracker

import bencode.decodeStream
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.io.UnsupportedEncodingException
import java.net.*
import java.nio.charset.Charset


internal class TrackerHTTP(listener: TrackerManager, private val baseUrl: String) : Tracker(listener) {
    private var trackerId: String? = null

    private var downloadedAtStart: Long = 0

    override suspend fun connect(information: TrackerTorrentInformation) {
        val fullUriB = StringBuilder()
        fullUriB.append(baseUrl)

        when (state) {
            TrackerState.STARTING -> {
                fullUriB.append("?event=started")
                downloadedAtStart = information.downloaded
            }
            TrackerState.STOPPING -> fullUriB.append("?event=stopped")
            TrackerState.COMPLETING -> fullUriB.append("?event=completed")
            else -> {
                fullUriB.append("?event=started")
                downloadedAtStart = information.downloaded
            }
        }

        try {
            fullUriB.append("&info_hash=").append(URLEncoder.encode(String(information.infoHash, Charset.forName("ISO-8859-1")), "ISO-8859-1"))  //TODO: Encoding, maybe write custom
                    .append("&peer_id=").append(information.peerId)
                    .append("&port=").append(information.port.toInt())
                    .append("&downloaded=").append(information.downloaded - downloadedAtStart)
                    .append("&uploaded=").append(information.uploaded)
                    .append("&left=").append(information.left)
                    .append("&compact=").append(if (information.isCompact) "1" else "0")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }

        if (information.key != 0) {
            fullUriB.append("&key=").append(information.key)
        }
        if (trackerId != null) {
            fullUriB.append(trackerId)
        }

        val fullUri = fullUriB.toString()

        try {
            val targetUrl = URL(fullUri).openConnection() as HttpURLConnection

            val item = decodeStream(targetUrl.inputStream) as? Map<String, Any> ?: throw AssertionError("Received object is not bencoded map!")
            targetUrl.disconnect()
            if (item.containsKey("failure reason")) {
                val error = (item.getValue("failure reason") as String)
                //this.listener.onFailedRequest(this, error)
                state = TrackerState.FAILED
                failureReason = error
            } else {
                lastResponseTime = System.nanoTime()

                val interval = item.getValue("interval") as Long?
                if (interval != null) {
                    this.interval = TimeUnit.SECONDS.toNanos(interval.toLong())
                } else {
                    this.interval = TimeUnit.MINUTES.toNanos(30)
                }

                val minInterval = item["min interval"] as Long?
                if (minInterval != null) {
                    this.minInterval = TimeUnit.SECONDS.toNanos(minInterval.toLong())
                }

                val trackerId = item["trackerid"] as String?
                if (trackerId != null) {
                    this.trackerId = trackerId
                }

                val complete = item["complete"] as Long?
                if (complete != null) {
                    this.seederCount = complete.toLong()
                }

                val incomplete = item["incomplete"] as Long?
                if (incomplete != null) {
                    this.leecherCount = incomplete.toLong()
                }

                when (state) {
                    TrackerState.COMPLETING, TrackerState.STARTING -> state = TrackerState.STARTED
                    TrackerState.STOPPING -> state = TrackerState.STOPPED
                }

                val peers = item["peers"] as String?
                if (peers != null) {
                    val foundPeers = mutableSetOf<SocketAddress>()
                    //TODO: check if it's not compact
                    val peerArray = peers.toByteArray(Charset.forName("ISO-8859-1"))
                    for (i in 0 until peerArray.size / 6) {
                        val peerAddress = InetAddress.getByAddress(Arrays.copyOfRange(peerArray, i * 6, i * 6 + 4))
                        val peerPort = ((peerArray[i * 6 + 4].toInt() and 0xff) shl 8) + (peerArray[i * 6 + 5].toInt() and 0xff)
                        foundPeers.add(InetSocketAddress(peerAddress, peerPort))
                    }
                    listener.onResponseReceived(foundPeers)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            //listener.onFailedRequest(this, e.toString())
            state = TrackerState.FAILED
            failureReason = e.message
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as TrackerHTTP?

        return baseUrl == that!!.baseUrl

    }

    override fun hashCode(): Int = baseUrl.hashCode()
}