package util

import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class BitField(val length: Int, val array: ByteArray = ByteArray((length + 7)/8)) {

    fun getBit(index: Int) : Boolean {
        if (index >= length || index < 0) {
            throw IndexOutOfBoundsException()
        }
        return (array[index / 8] and (1 shl (7 - index.rem(8))).toByte()) != 0.toByte()
    }

    fun setBit(index: Int)
    {
        if (index >= length) {
            throw IndexOutOfBoundsException()
        }
        array[index/8] = array[index/8] or (1 shl (7 - index.rem(8))).toByte()
    }

    fun clearBit(index: Int)
    {
        if (index >= length) {
            throw IndexOutOfBoundsException()
        }
        array[index/8] = array[index/8] and (1 shl (7 - index.rem(8))).toByte().inv()
    }

    fun nextSetBit(from: Int): Int {
        var index = from
        while (index < length && !getBit(index)){
            index++
        }
        if (index == length) return -1
        else return index
    }

    fun or(bitfield: BitField) {
        var index = 0
        while (index < array.size){
            array[index] = array[index] or bitfield.array[index]
            index++
        }
    }
}