package org.descendant.bootanims

import android.graphics.Rect
import android.util.Log
import java.io.BufferedReader
import java.util.concurrent.ConcurrentHashMap

class AnimDescriptorEntry(
    val command: Char,
    val count: Int?,
    val pause: Int,
    val path: String,
    val background: Int = 0
) {

    var trim: ConcurrentHashMap<Int, Rect>? = null

    companion object {
        fun fromString(desc: String): AnimDescriptorEntry {

            val elems = desc.trim().split(" ")

            var count: Int?
            val pause: Int
            var background = 0

            if ((elems.isNotEmpty()) and (elems.size < 4))
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Not enough fields")
            if (elems[0].length != 1)
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid operation type")
            try {
                count = elems[1].toInt()
                if (count == 0) {
                    count = null
                }
            } catch (e: Exception) {
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid count")
            }
            try {
                pause = elems[2].toInt()
            } catch (e: Exception) {
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid pause")
            }
            if (elems[3].isEmpty())
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid path")
            if (elems.size > 4) {
                if (elems[4].length != 6)
                    throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid RGBHEX")
                try {
                    background = elems[4].toInt(16)
                } catch (e: Exception) {
                    throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Failed to parse RGBHEX")
                }
                if (elems.size > 5) {
                    //TODO: CLOCK1
                    if (elems.size > 6) {
                        //TODO: CLOCK2
                    }
                }
            }

            return AnimDescriptorEntry(elems[0].toCharArray()[0], count, pause, elems[3], background)
        }
    }
}

class AnimDescriptor(val width: Int, val height: Int, val fps: Int, val entries: ArrayList<AnimDescriptorEntry>) {
    val defaultRect: Rect = Rect(0, 0, width, height)

    companion object {

        fun fromReader(desc: BufferedReader): AnimDescriptor {
            var i = 0
            var elems: List<String>?
            var ent: AnimDescriptor? = null
            var width: Int
            var height: Int
            var fps: Int



            for (line in desc.lines()) {
                Log.e("bootanimationdescriptor", line)
                if (i == 0) {
                    elems = line.trim().split(" ")
                    if (elems.size != 3)
                        throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Wrong header length $line")
                    try {
                        width = elems[0].trim().toInt()
                    } catch (e: Exception) {
                        throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid width")
                    }
                    try {
                        height = elems[1].trim().toInt()
                    } catch (e: Exception) {
                        throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid height")
                    }
                    try {
                        fps = elems[2].trim().toInt()
                    } catch (e: Exception) {
                        throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Invalid FPS")
                    }
                    ent = AnimDescriptor(width, height, fps, ArrayList())
                } else {
                    ent!!.entries.add(AnimDescriptorEntry.fromString(line))
                }
                i++
            }
            if (ent == null)
                throw InvalidAnimationException(AnimErrors.BROKEN_DESC, "Empty desc")
            return ent
        }
    }
}