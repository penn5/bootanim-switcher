package org.descendant.bootanims

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.collections.HashMap
import kotlin.concurrent.thread

/**
 * Plays a bootanimation. WIP. Use setAnimation from Java to start
 */
class BootanimationView : View {

    private var desc: AnimDescriptor? = null

    private val tag = "BootanimationView"
    private val descFile = "desc.txt"

    private var path: File? = null
    private var zipFile: ZipFile? = null
    private var zipEntries: Enumeration<out ZipEntry>? = null
    private var zippedImgs: HashMap<String, ZipEntry>? = null

    private var currentEntry: AnimDescriptorEntry? = null
    private var currentEntryNum = 0
    private var nextFrame = 0

    private val frames = ConcurrentHashMap<String, Bitmap>()

    private var virtualBootComplete = false
    private var ended = true
    private var stopped = true

    private var curCount = 1
    private var delaying = -1

    private var loaderThread: Thread? = null

    private var lastBitmap: Bitmap? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.sourcePath = path?.path ?: return superState
        savedState.entryNum = currentEntryNum
        savedState.frameNum = nextFrame
        savedState.ended = ended
        savedState.stopped = stopped
        savedState.booted = virtualBootComplete
        savedState.loopCount = curCount
        savedState.delayed = delaying
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState((state as? SavedState)?.superState ?: state)
        if (state is SavedState) {
            setAnimation(File(state.sourcePath))
            switchEntry(state.entryNum)
            nextFrame = state.frameNum
            ended = state.ended
            stopped = state.stopped
            virtualBootComplete = state.booted
            curCount = state.loopCount
            delaying = state.delayed
            if (ended.not())
                tick() // Resume

        }

    }


    internal class SavedState : View.BaseSavedState {

        var sourcePath: String = ""
        var entryNum: Int = 0
        var frameNum: Int = 0
        var ended: Boolean = false
        var stopped: Boolean = false
        var booted: Boolean = false
        var loopCount: Int = 0
        var delayed: Int = 0


        constructor(source: Parcel) : super(source) {
            sourcePath = source.readString()!!
            entryNum = source.readInt()
            frameNum = source.readInt()
            ended = source.readByte().toInt() > 0
            stopped = source.readByte().toInt() > 0
            booted = source.readByte().toInt() > 0
            loopCount = source.readInt()
            delayed = source.readInt()
        }

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(sourcePath)
            out.writeInt(entryNum)
            out.writeInt(frameNum)
            out.writeByte((if (ended) 1 else 0).toByte())
            out.writeByte((if (stopped) 1 else 0).toByte())
            out.writeByte((if (booted) 1 else 0).toByte())
            out.writeInt(loopCount)
            out.writeInt(delayed)
        }

        companion object {
            @Suppress("unused") // It is required to be Parcelable. It is read by Android.
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {

                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }


    public fun setAnimation(file: File) {
        path = file
        zipFile = ZipFile(file)
        val inputStream = zipFile!!.getInputStream(zipFile!!.getEntry(descFile))
        desc = AnimDescriptor.fromReader(BufferedReader(InputStreamReader(inputStream)))

        zipEntries = zipFile!!.entries()
        zippedImgs = HashMap()
        val descPaths = desc!!.entries.map { it.path }
        var folder: String
        val i = HashMap<String, Int>()
        for (entry in zipEntries!!) {
            if (entry.isDirectory.not()) {
                if (entry.method == ZipEntry.STORED) {
                    if (entry.name.contains("/")) {
                        if (entry.name.split("/").first() in descPaths) {
                            if ((entry.name.split("/").last().contains("audio.wav").not()) && (entry.name.split("/").last().contains(
                                    "trim.txt"
                                ).not())
                            ) {
                                // YUS! It's a image we want!
                                folder = entry.name.split("/").first()
                                zippedImgs!![entry.name.split("/").first() + "/" + (i[folder] ?: 0).toString()] =
                                    entry // i starts at 1
                                i[folder] = (i[folder] ?: 0) + 1
                                Log.e(tag, folder + (i[folder].toString()))
                            }
                        }
                    }
                }
            }
        }

        prepareEntry(0)
        currentEntry = desc!!.entries[0]
        currentEntryNum = 0
        nextFrame = 0

        startLoaderThread()

    }


    private fun loadBitmap(name: String): Bitmap? {
        return BitmapFactory.decodeStream(zipFile!!.getInputStream(zippedImgs!![name] ?: return null))
    }


    private fun startLoaderThread() {
        loaderThread?.interrupt()
        loaderThread = thread(start = true) {

            for (img in zippedImgs!!.keys) {
                if (Thread.currentThread().isInterrupted)
                    break
                frames[img] = loadBitmap(img) ?: continue
            }
        }
    }

    private fun switchEntry(entry: Int) {
        prepareEntry(entry)
        currentEntryNum = entry
        currentEntry = desc!!.entries[entry]
        curCount = 1
        setBackgroundColor(currentEntry!!.background)
    }

    private fun prepareEntry(entry: Int) {
        //currentEntry = desc!!.entries[entry]
        val trimEntry = zipFile!!.getEntry(desc!!.entries[entry].path)
        if (trimEntry != null) {
            val trimStream = BufferedReader(InputStreamReader(zipFile!!.getInputStream(trimEntry)))
            var trimTmp: List<String>
            desc!!.entries[entry].trim = ConcurrentHashMap()
            var i = 0
            for (line in trimStream.lines()) {
                if (line.isNotEmpty()) {
                    trimTmp = line.split(delimiters = *arrayOf("x", "+"))
                    desc!!.entries[entry].trim[i++] =
                        Rect(trimTmp[0].toInt(), trimTmp[1].toInt(), trimTmp[2].toInt(), trimTmp[3].toInt())
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (zipFile == null)
            ended = true
        if (currentEntry == null)
            ended = true
        if ((delaying > -1) && (delaying < currentEntry!!.pause)) {
            delaying++
        } else {
            delaying = -1
        }
        if (ended || ((delaying > -1) && (delaying < currentEntry!!.pause))) {
            canvas.drawBitmap(
                lastBitmap ?: return,
                null,
                currentEntry!!.trim.getOrDefault(nextFrame, desc!!.defaultRect),
                null
            )
            return
        }

        if (frames.containsKey(currentEntry!!.path + "/" + nextFrame.toString())) {
            lastBitmap = frames[currentEntry!!.path + "/" + nextFrame.toString()]!!
            canvas.drawBitmap(
                lastBitmap!!, null,
                currentEntry!!.trim.getOrDefault(nextFrame, desc!!.defaultRect), null
            )
            if ((currentEntry!!.count == curCount) || virtualBootComplete) {
                frames.remove(currentEntry!!.path + "/" + nextFrame.toString())
            }
            Log.w(tag, "Fast way")
        } else {
            Log.e(tag, currentEntry!!.path + "/" + nextFrame.toString())
            // We don't care, it's only done if the background thread is behind.
            // Quick fail out due to risk of IndexOutOfBoundException
            (loadBitmap(currentEntry!!.path + "/" + nextFrame.toString()) ?: {
                Log.w(tag, "tmpzipentry null, starting again from $nextFrame")

                if (currentEntry!!.command == 'p' && virtualBootComplete) { // In theory only c and p work, but aosp does this.
                    stop()
                }

                if ((currentEntry!!.count == curCount) || virtualBootComplete) {
                    if (currentEntryNum + 1 >= desc!!.entries.size)
                        stop() // Index out of bounds here because we have finished the animation, stop *right now*
                    else
                        switchEntry(currentEntryNum + 1)
                } else {
                    curCount += 1
                }

                nextFrame = 0
                if (ended.not())
                    loadBitmap(currentEntry!!.path + "/" + nextFrame.toString())!!
                else
                    null
            }())?.let { lastBitmap = it }
            canvas.drawBitmap(
                lastBitmap!!,
                null,
                currentEntry!!.trim.getOrDefault(nextFrame, desc!!.defaultRect),
                null
            )
            Log.w(tag, "Slow way")
        }
        Log.w(tag, "FRAME!!!!")
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        if ((widthMode == MeasureSpec.EXACTLY) or (heightMode == MeasureSpec.EXACTLY))
            throw IllegalStateException("View cannot have size set exactly.")
        if (desc != null) {
            if ((widthMode == MeasureSpec.AT_MOST) and (widthSize < desc!!.width))
                throw IllegalStateException("Parent view is too thin to display this!")
            if ((heightMode == MeasureSpec.AT_MOST) and (heightSize < desc!!.height))
                throw IllegalStateException("Parent view is too short to display this! $heightSize < ${desc!!.height}")
            setMeasuredDimension(desc!!.width, desc!!.height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    public fun start() {
        if (frames.size == 0) {
            startLoaderThread()
        }
        ended = false
        stopped = false
        virtualBootComplete = false
        tick()
    }

    public fun restart() {
        switchEntry(0)
        nextFrame = 0
        start()
    }

    public fun stop() {
        loaderThread?.interrupt()
        frames.clear()
        ended = true
        stopped = true
    }

    public fun pause() {
        ended = true
    }

    public fun end() {
        virtualBootComplete = true
    }

    public fun isEnded(): Boolean {
        // Ended is actually paused
        return ended
    }

    public fun isStopped(): Boolean {
        // Stopped is when it's complete.
        return stopped
    }

    private fun tick() {
        if (ended.not()) {
            postDelayed({ tick() }, 1000L / desc!!.fps)
            Log.w(tag, "FPS!")
            nextFrame++
            invalidate()
        }
    }

    public fun loadFirstFrame() {
        lastBitmap = loadBitmap(desc!!.entries[0].path + "/0")
    }
}
