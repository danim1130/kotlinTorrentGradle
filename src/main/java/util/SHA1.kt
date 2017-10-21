package util

import java.nio.ByteBuffer
import java.util.Arrays
import java.security.MessageDigest

data class SHA1 private constructor(private val value: IntArray) {

    private val byteRepresentation = ByteBuffer.allocate(20).apply{this.asIntBuffer().put(value)}.array()

    val byteValue = Arrays.copyOf(byteRepresentation, byteRepresentation.size)!!

     val readableRepresentation = StringBuilder(40).apply{
         for (i in value.indices)
             this.append(String.format("%08x", value[i]))
     }.toString()

    fun writeToBuffer(buffer: ByteBuffer) {
        for (i in value.indices) {
            buffer.putInt(value[i])
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SHA1

        if (!Arrays.equals(value, other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(value)
    }


    companion object {

        fun fromByteArray(arr: ByteArray): SHA1 {
            if (arr.size != 20) {
                throw AssertionError("Length of byte array is not 20")
            }
            val intArr = IntArray(5)
            ByteBuffer.wrap(arr).asIntBuffer().get(intArr)
            return fromIntArray(intArr)
        }

        fun fromByteBuffer(buffer: ByteBuffer): SHA1 {
            if (buffer.remaining() < 20){
                throw AssertionError("Length of byte array is not 20")
            }
            val intBuffer = buffer.asIntBuffer()
            val intArr = IntArray(5)
            intBuffer.get(intArr)
            buffer.position(buffer.position() + 20)
            return fromIntArray(intArr)
        }

        private fun fromIntArray(arr: IntArray): SHA1 {
            if (arr.size != 5) {
                throw AssertionError("Length of int array is not 5")
            }
            return SHA1(arr)
        }

        fun getHash(msg: ByteArray): SHA1 {
            val hasher = MessageDigest.getInstance("SHA1")
            hasher.reset()
            return fromByteArray(hasher.digest(msg))
        }

        fun getHash(msg: String): SHA1? {
            return getHash(msg.toByteArray())
        }
    }
}