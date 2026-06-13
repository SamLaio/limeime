/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.keyboard

import android.view.MotionEvent
import kotlin.math.max
import kotlin.math.min

class SwipeTracker {
    @JvmField
    val mBuffer = EventRingBuffer(NUM_PAST)

    var xVelocity: Float = 0f
        private set

    var yVelocity: Float = 0f
        private set

    fun addMovement(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            mBuffer.clear()
            return
        }
        val time = ev.eventTime
        val count = ev.historySize
        for (i in 0 until count) {
            addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i))
        }
        addPoint(ev.x, ev.y, time)
    }

    private fun addPoint(x: Float, y: Float, time: Long) {
        val buffer = mBuffer
        while (buffer.size() > 0) {
            val lastT = buffer.getTime(0)
            if (lastT >= time - LONGEST_PAST_TIME) {
                break
            }
            buffer.dropOldest()
        }
        buffer.add(x, y, time)
    }

    fun computeCurrentVelocity(units: Int) {
        computeCurrentVelocity(units, Float.MAX_VALUE)
    }

    fun computeCurrentVelocity(units: Int, maxVelocity: Float) {
        val buffer = mBuffer
        val oldestX = buffer.getX(0)
        val oldestY = buffer.getY(0)
        val oldestTime = buffer.getTime(0)

        var accumX = 0f
        var accumY = 0f
        val count = buffer.size()
        for (pos in 1 until count) {
            val dur = (buffer.getTime(pos) - oldestTime).toInt()
            if (dur == 0) continue
            var dist = buffer.getX(pos) - oldestX
            var vel = dist / dur * units
            accumX = if (accumX == 0f) vel else (accumX + vel) * .5f

            dist = buffer.getY(pos) - oldestY
            vel = dist / dur * units
            accumY = if (accumY == 0f) vel else (accumY + vel) * .5f
        }
        xVelocity = if (accumX < 0.0f) max(accumX, -maxVelocity) else min(accumX, maxVelocity)
        yVelocity = if (accumY < 0.0f) max(accumY, -maxVelocity) else min(accumY, maxVelocity)
    }

    class EventRingBuffer(private val bufSize: Int) {
        private val xBuf = FloatArray(bufSize)
        private val yBuf = FloatArray(bufSize)
        private val timeBuf = LongArray(bufSize)
        private var top = 0
        private var end = 0
        private var count = 0

        init {
            clear()
        }

        fun clear() {
            top = 0
            end = 0
            count = 0
        }

        fun size(): Int = count

        private fun index(pos: Int): Int = (end + pos) % bufSize

        private fun advance(index: Int): Int = (index + 1) % bufSize

        fun add(x: Float, y: Float, time: Long) {
            xBuf[top] = x
            yBuf[top] = y
            timeBuf[top] = time
            top = advance(top)
            if (count < bufSize) {
                count++
            } else {
                end = advance(end)
            }
        }

        fun getX(pos: Int): Float = xBuf[index(pos)]

        fun getY(pos: Int): Float = yBuf[index(pos)]

        fun getTime(pos: Int): Long = timeBuf[index(pos)]

        fun dropOldest() {
            count--
            end = advance(end)
        }
    }

    companion object {
        private const val NUM_PAST = 4
        private const val LONGEST_PAST_TIME = 200
    }
}
