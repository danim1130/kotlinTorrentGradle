package piece
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import torrent.FileData
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class PieceToFileMapper(private val fileEntryList: List<FileData>) {

    init{
        fileEntryList.forEach {
            val file = File(it.name)
            if (file.parentFile != null) {
                Files.createDirectories(file.parentFile.toPath())
            }
            file.createNewFile()
        }
    }

    fun writePiece(data: ByteArray, begin: Long) = launch(CommonPool){
        var baseOffset: Long = 0
        var i = 0
        while (baseOffset < begin + data.size) {
            val entry = fileEntryList[i]
            if (baseOffset + entry.length > begin && baseOffset <= begin) { //Starting to write
                val offsetInFile = begin - baseOffset
                val toWrite: ByteBuffer
                if (entry.length - offsetInFile >= data.size) {
                    toWrite = ByteBuffer.wrap(data)
                } else {
                    toWrite = ByteBuffer.wrap(data, 0, (entry.length - offsetInFile).toInt())
                }
                val channel = AsynchronousFileChannel.open(File(entry.name).toPath(),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                var written = 0
                while (toWrite.hasRemaining()) written += channel.aWrite(toWrite, offsetInFile + written)
            } else if (baseOffset > begin) {
                val offsetInData = (baseOffset - begin).toInt()
                val toWriteLength = Math.min(entry.length, (data.size - offsetInData).toLong()).toInt()
                try {
                    val toWrite = ByteBuffer.wrap(data, offsetInData, toWriteLength)
                    val channel = AsynchronousFileChannel.open(File(entry.name).toPath(),
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                    var written = 0
                    while (toWrite.hasRemaining()) written += channel.aWrite(toWrite, written.toLong())
                } catch (e: Exception){
                    e.printStackTrace()
                }
            }
            i++
            baseOffset += entry.length
        }
    }

    suspend fun readBlock(begin: Long, length: Int) : ByteArray{ //TODO: make file reads paralell
        val buffer = ByteArray(length)
        val jobs = mutableListOf<Job>()

        var baseOffset: Long = 0
        var i = 0
        while (baseOffset < begin + length) {
            val entry = fileEntryList[i]
            if (baseOffset + entry.length > begin && baseOffset <= begin) { //Starting to write
                val offsetInFile = begin - baseOffset
                val readLength = Math.min(entry.length - offsetInFile, length.toLong())

                val toRead = ByteBuffer.wrap(buffer, 0, readLength.toInt())
                val channel = AsynchronousFileChannel.open(File(entry.name).toPath())

                val readJob = launch(CommonPool) {while (toRead.hasRemaining()) channel.aRead(toRead, offsetInFile)}
                jobs.add(readJob)
            } else if (baseOffset > begin) {
                val offsetInData = (baseOffset - begin).toInt()
                val toReadLength = Math.min(entry.length, (length - offsetInData).toLong()).toInt()

                val toRead = ByteBuffer.wrap(buffer, offsetInData, toReadLength)
                val channel = AsynchronousFileChannel.open(File(entry.name).toPath())

                val readJob = launch(CommonPool){while (toRead.hasRemaining()) channel.aRead(toRead, 0)}
                jobs.add(readJob);
            }
            i++
            baseOffset += entry.length
        }
        jobs.forEach { it.join() }
        return buffer
    }
}