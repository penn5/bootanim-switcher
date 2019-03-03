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
import java.lang.ref.WeakReference
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

    private var toRecycle: WeakReference<Bitmap?> = WeakReference(null)

    private var transformW: Int = 0
    private var transformH: Int = 0

    private var lastFrame = 0

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
                            if ((entry.name.split("/").last().contains("audio.wav").not()) &&
                                (entry.name.split("/").last().contains(
                                    "trim.txt"
                                ).not())
                            ) {
                                // YUS! It's a image we want!
                                folder = entry.name.split("/").first()
                                zippedImgs!![entry.name.split("/").first() + "/" +
                                        (i[folder] ?: 0).toString()] = entry // i starts at 1
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
        loaderThread = thread(start = false) {
            /*try {
                var entry = currentEntryNum
                var frame = nextFrame + 1
                var iEnded = false
                while (true) {
                    (loadBitmap(desc!!.entries[entry].path + "/" + frame.toString()) ?: {
                        Log.w(tag, "tmpzipentry null, starting again from $nextFrame")
                        if (entry + 1 >= desc!!.entries.size)
                            iEnded = true
                        else {
                            entry++
                        }

                        nextFrame = 0
                        if (iEnded.not())
                            loadBitmap(currentEntry!!.path + "/" + nextFrame.toString())!!
                        else
                            null
                    }())?.let { frames[desc!!.entries[entry].path + "/" + frame.toString()] = it }
                    if (iEnded)
                        break
                    frame++
                    if (entry < currentEntryNum) {
                        entry = currentEntryNum
                        frame = nextFrame + 2
                    }
                    if (entry == currentEntryNum && frame < nextFrame)
                        Log.e(tag, "background thread behind on rendering")
                    frame = nextFrame + 2
                    while (frames.size > 10) {
                        //Thread.sleep(1000L / desc!!.fps)
                    }
                }
            } catch (e: InterruptedException) {
                //ignore
            }*/



            try {
                for (img in zippedImgs!!.keys.sorted()) {
                    while (frames.size > 5 && frames.filter { it.value.isRecycled.not() }.size > 5) {
                        Log.e(tag, "background thread sleeping $frames")
                        Thread.sleep(1000L / desc!!.fps) // Buffer between 5 and 4 frames
                        if (Thread.currentThread().isInterrupted)
                            break
                    }
                    if (Thread.currentThread().isInterrupted)
                        break
                    Log.e(tag, "background thread working")
                    frames[img] = loadBitmap(img) ?: continue
                }
            } catch (e: InterruptedException) {
                // Ignore
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


    //@SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /*if (ended || ((delaying > -1) && (delaying < currentEntry!!.pause))) {
            canvas.drawBitmap(
                lastBitmap ?: return,
                null,
                currentEntry!!.trim.getOrDefault(nextFrame, desc!!.defaultRect),
                null
            )
            return
        }*/

/*
        if (frames.containsKey(currentEntry!!.path + "/" + nextFrame.toString())) {

            toRecycle = WeakReference(lastBitmap)
            lastBitmap = frames[currentEntry!!.path + "/" + nextFrame.toString()]!!
            lastBitmap = lastBitmap!!.copy(lastBitmap!!.config, true)


            if ((currentEntry!!.count == curCount) || virtualBootComplete) {
                frames[currentEntry!!.path + "/" + nextFrame.toString()]?.recycle()
                frames.remove(currentEntry!!.path + "/" + nextFrame.toString())
            }
            Log.w(tag, "Fast rendering")
        } else {
            Log.e(tag, currentEntry!!.path + "/" + nextFrame.toString())
            // We don't care, it's only done if the background thread is behind.
            // Quick fail out due to risk of IndexOutOfBoundException
            toRecycle = WeakReference(lastBitmap)
            lastBitmap = loadBitmap(currentEntry!!.path + "/" + nextFrame.toString())

            Log.w(tag, "Slow rendering ${currentEntry!!.path + "/" + nextFrame.toString()}")
        }*/
        canvas.drawBitmap(
            Bitmap.createScaledBitmap(lastBitmap!!, transformW, transformH, false),
            null,
            currentEntry!!.trim.getOrDefault(nextFrame, desc!!.defaultRect), null
        )
        lastFrame = nextFrame

        //toRecycle.get()?.recycle()
    }

    override fun getSuggestedMinimumHeight(): Int {
        return desc?.height ?: super.getSuggestedMinimumHeight()
    }

    override fun getSuggestedMinimumWidth(): Int {
        return desc?.width ?: super.getSuggestedMinimumWidth()
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        var heightSize = MeasureSpec.getSize(heightMeasureSpec)


        if (desc != null) {

            val ratioH = desc!!.height / desc!!.width
            val ratioW = desc!!.width / desc!!.height

            if (heightMode == MeasureSpec.UNSPECIFIED) {
                heightSize = desc!!.height
            }
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                widthSize = desc!!.width
            }

            val rRatioW = widthSize / heightSize
            val rRatioH = heightSize / widthSize

            if (rRatioH > ratioH) { // If the ratioH we want is bigger than the ratioH we are are given, the provided frame is too wide, and we must reduce height to fit it
                heightSize = ratioH * widthSize
            }
            if (rRatioW > ratioW) { // Likewise, if the ratioW is too large, we must reduce the image width
                widthSize = ratioW * heightSize
            }
            if (rRatioH != ratioH || rRatioW != ratioW)
                throw RuntimeException("programming error!")
            // If they're the same, no work to do! yay
        }
        if (desc == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else {
            transformW = widthSize
            transformH = heightSize
            setMeasuredDimension(widthSize, heightSize)
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
        for (frame in frames.values) {
            frame.recycle()
        }
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
            nextFrame++

            thread(start = true) {
                if (zipFile == null)
                    stop()
                if (currentEntry == null)
                    stop()
                if ((delaying > -1) && (delaying < currentEntry!!.pause)) {
                    delaying++
                } else {
                    delaying = -1

                    if ((currentEntry!!.path + "/" + nextFrame in zippedImgs!!.keys).not()) {
                        nextFrame = 0

                        if (currentEntry!!.command == 'p' && virtualBootComplete) { // Only c and p are allowed; aosp does this
                            stop()
                        }

                        if ((currentEntry!!.count == curCount) || virtualBootComplete) {
                            if (currentEntryNum + 1 >= desc!!.entries.size)
                                stop() // Index out of bounds here because we have finished the animation, stop *right now*
                            else {
                                switchEntry(currentEntryNum + 1)
                                /*for (frame in frames) {
                            if (frame.key.split("/").first() == desc!!.entries[currentEntryNum - 1].path) {
                                frame.value.recycle()
                            }
                        }*/
                            }
                        } else {
                            curCount += 1
                        }
                    }
                    if (ended.not()) {
                        (loadBitmap(currentEntry!!.path + "/" + nextFrame))?.let { lastBitmap = it }
                        invalidate()
                    }
                }
            }
        }
    }

    public fun loadFirstFrame() {
        lastBitmap = loadBitmap(desc!!.entries[0].path + "/0")
    }
}
