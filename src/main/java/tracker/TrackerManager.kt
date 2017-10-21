package tracker

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import torrent.Torrent
import torrent.message.PeersDiscovered
import java.net.SocketAddress

/**
 * Created by danim on 13/01/2017.
 */
class TrackerManager(val torrent: Torrent, val trackerAddresses : List<List<String>>) {

    private val trackers = trackerAddresses.map {it.map { Tracker.fromAddress(this@TrackerManager, it) }.shuffled().toMutableList()};

    private var connectedTracker : Tracker? = null;
    private var updateTrackerJob : Job? = null;

    fun startManager(){
        launch(CommonPool) {
            while(connectedTracker == null) {
                getPeers(100)
                if (connectedTracker == null) {
                    delay(5 * 60 * 1000)
                }
            }
            updateTrackerAfterInterval()
        }
    }

    private suspend fun getPeers(wantNum : Int = 100){
        val information = defaultTrackerRequest.copy(peerWanted = wantNum)
        sendMessage(information)
    }

    private fun updateTrackerAfterInterval(){
        updateTrackerJob = launch(CommonPool) {
            val tracker = connectedTracker;
            if (tracker == null){
                startManager() //Shouldn't happen
                println("connectedTracker is null")
                return@launch;
            }

            delay(tracker.interval)
            getPeers(0);
        }
    }

    private suspend fun sendMessage(information : TrackerTorrentInformation) = synchronized(this){
        val job = updateTrackerJob
        if (job != null && job.isActive){
            updateTrackerJob = null
            job.cancel()
        }

        for (tierList in trackers) {
            for (tracker in tierList) {
                tracker.sendInformation(information)
                if (!tracker.hasFailed()) {
                    connectedTracker = tracker
                    break;
                }
            }
            if (connectedTracker != null) {
                tierList.remove(connectedTracker!!)
                tierList.add(0, connectedTracker!!)
                updateTrackerAfterInterval()
                break;
            }
        }
    }

    fun onResponseReceived(peers : Set<SocketAddress>) {
        if (peers.isNotEmpty()) launch(CommonPool){torrent.channel.send(PeersDiscovered(peers))}
    }

    private val defaultTrackerRequest get() = synchronized(torrent) {
        TrackerTorrentInformation(
                infoHash = torrent.data.infoHash.byteValue,
                peerId = torrent.peerId,
                port = 6881,
                downloaded = torrent.downloaded,
                uploaded = torrent.uploaded,
                left = torrent.left,
                isCompact = true,
                peerWanted = 100)
    }
}