package org.descendant.bootanims

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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

/**
 * Plays a bootanimation. WIP. Use setAnimation from Java to start
 */
class BootanimationView : View {

    private var desc: AnimDescriptor? = null

    private val tag = "BootanimationView"
    private val descFile = "desc.txt"

    private var zipFile: ZipFile? = null
    private var zipEntries: Enumeration<out ZipEntry>? = null
    private var zippedImgs: HashMap<String, ZipEntry>? = null

    private var currentEntry: AnimDescriptorEntry? = null
    private var currentEntryNum = 0
    private var nextFrame = 0

    private var bitmapFactory: BitmapFactory? = null

    private var tmpZipEntry: ZipEntry? = null
    private var tmpBitmap: Bitmap? = null

    val frames = ConcurrentHashMap<Int, Bitmap>()

    private var virtualBootComplete = false
    private var ended = false

    private var curCount = 1

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        //TODO
    }

    fun setAnimation(file: File) {
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
                                i.put(folder, (i[folder] ?: 0) + 1)
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


    }

    fun switchEntry(entry: Int) {
        prepareEntry(entry)
        currentEntryNum = entry
        currentEntry = desc!!.entries[entry]
        curCount = 1
        setBackgroundColor(currentEntry!!.background)
    }

    fun prepareEntry(entry: Int) {
        //currentEntry = desc!!.entries[entry]
        val trimEntry = zipFile!!.getEntry(desc!!.entries[entry].path)
        if (trimEntry != null) {
            // There's a trim file!
            //TODO: load it
        } else {
            val trimStream = zipFile!!.getInputStream(trimEntry)
            var trimString = ""
            while (trimStream.available() > 0) {
                trimString += trimStream.read()
            }
            var trimTmp: List<String>
            desc!!.entries[entry].trim = ConcurrentHashMap()
            for ((i, line) in trimString.lines().withIndex()) {
                if (line.isNotEmpty()) {
                    trimTmp = line.split(delimiters = *arrayOf("x", "+"))
                    desc!!.entries[entry].trim!![i] =
                        Rect(trimTmp[0].toInt(), trimTmp[1].toInt(), trimTmp[2].toInt(), trimTmp[3].toInt())
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (zipFile == null)
            return
        if (currentEntry == null)
            return

        if (frames.containsKey(nextFrame))
            canvas.drawBitmap(
                frames[nextFrame]!!, null,
                currentEntry!!.trim!!.getOrDefault(nextFrame, desc!!.defaultRect), null
            )
        else {
            // We don't care, it's only done if the background thread is behind.
            tmpZipEntry = zippedImgs!![currentEntry!!.path + "/" + nextFrame.toString()]
            if (tmpZipEntry == null) {
                Log.w(tag, "tmpzipentry null, starting again from $nextFrame")

                if (currentEntry!!.command == 'p' && virtualBootComplete) { // In theory only c and p work, but aosp does this.
                    stop()
                }

                if ((currentEntry!!.count == curCount) || virtualBootComplete) {
                    if (currentEntryNum + 1 >= desc!!.entries.size) {
                        stop()
                        return
                    }
                    switchEntry(currentEntryNum + 1)
                } else {
                    curCount += 1
                }

                nextFrame = 0
                tmpZipEntry = zippedImgs!![currentEntry!!.path + "/" + nextFrame.toString()]
            }
            tmpBitmap = BitmapFactory.decodeStream(zipFile!!.getInputStream(tmpZipEntry))
            canvas.drawBitmap(
                tmpBitmap!!,
                null,
                currentEntry!!.trim?.getOrDefault(nextFrame, desc!!.defaultRect) ?: desc!!.defaultRect,
                null
            )
        }
        nextFrame++
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

    fun start() {
        tick()
    }

    fun stop() {
        ended = true
    }

    fun end() {
        virtualBootComplete = true
    }

    private fun tick() {
        if (ended.not()) {
            postDelayed({ tick() }, 1000L / desc!!.fps)
            nextFrame++
            invalidate()
        }
    }
}