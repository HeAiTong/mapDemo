package com.example.mapdemo

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan

class CameraRouteHelper(
    val points: List<DPPoint>,
    private val rectCenter: PointD,
    videoLength: Double
) {
    private val rotationIndexes: ArrayList<Int>
    private val needStable: Boolean

    init {
        rotationIndexes = calculateRotationIndexes(videoLength)
        needStable = videoLength > 50
    }

    private fun calculateRotationIndexes(
        videoLength: Double,
        interval: Double = 3.0
    ): ArrayList<Int> {
        if (videoLength <= 0 || points.count() <= 2) return arrayListOf()
        val indexes = arrayListOf<Int>()
        val fullKey = points.last().dpKey
        val intervalKey = fullKey / videoLength * interval
        for (i in 1 until points.lastIndex) {
            if (points[i + 1].dpKey - points[i].dpKey >= intervalKey) {
                indexes.add(i)
            } else if (points[i].dpKey - points[indexes.lastOrNull() ?: 0].dpKey >= intervalKey) {
                indexes.add(i)
            }
        }
        Log.d("camera", "rotation index: $indexes")
        return indexes
    }

    fun getStartPosition(): CameraPosition? {
        return if (needStable) {
            CameraPosition(0, rectCenter, 0f)
        } else {
            if (points.isEmpty()) return null
            if (points.count() <= 1) return CameraPosition(0, points[0].dpValue, null)
            val firstPoint = points[0].dpValue
            val secondPoint = points[1].dpValue
            val angle = getAngle(firstPoint, secondPoint).toFloat()
            CameraPosition(0, firstPoint, -angle)
        }
    }

    fun getPosition(key: Double, lastIndex: Int): CameraPosition? {
        return if (needStable) {
            CameraPosition(lastIndex, rectCenter, 0f)
        } else {
            if (points.isEmpty() || lastIndex < 0) return null
            if (points.count() <= 1 || lastIndex >= points.lastIndex)
                return CameraPosition(0, points[0].dpValue, null)
            var pIndex: Int? = null
            for (index in lastIndex until points.lastIndex) {
                if (key < points[index].dpKey || key > points[index + 1].dpKey) continue
                pIndex = index
                break
            }
            val currentIndex = pIndex
                ?: return CameraPosition(points.lastIndex, points.last().dpValue, null)
            if (points[currentIndex].dpKey == points[currentIndex + 1].dpKey) {
                return CameraPosition(currentIndex, points[currentIndex].dpValue, null)
            }

            val current = points[currentIndex]
            val next = points[currentIndex + 1]
            val ratio = (key - current.dpKey) / (next.dpKey - current.dpKey)
            val point = current.dpValue.interpolate(next.dpValue, ratio)

            val angle = getAngle(currentIndex, lastIndex)

            Log.d("camera", angle.toString())
            return CameraPosition(currentIndex, point, angle)
        }
    }

    private fun getBaiduAngle(currentIndex: Int, lastIndex: Int): Float? {
        val current = points[currentIndex]
        val next = points[currentIndex + 1]
        return if (lastIndex != currentIndex && rotationIndexes.contains(currentIndex)) {
            -getAngle(current.dpValue, next.dpValue).toFloat()
        } else {
            null
        }
    }

    private var beginRotate = false
    private var angleIndex = 0
    private var lastAngle = getStartPosition()?.rotation?.toDouble() ?: 0.0
    private fun getAngle(currentIndex: Int, lastIndex: Int): Float? {
        val current = points[currentIndex]
        val next = points[currentIndex + 1]

        if (lastIndex != currentIndex && rotationIndexes.contains(currentIndex)) {
            beginRotate = true
        }
        return if (lastIndex != currentIndex && rotationIndexes.contains(currentIndex) || (beginRotate && angleIndex < 21)) {
            angleIndex += 1
            val nextAngle = -getAngle(current.dpValue, next.dpValue)
            val targetAngle = if (nextAngle - lastAngle > 180 || nextAngle - lastAngle < -180) {
                if (nextAngle > 0) {
                    -(360 - abs(nextAngle))
                } else {
                    (360 - abs(nextAngle))
                }
            } else {
                nextAngle
            }
            val diff = targetAngle - lastAngle
            (lastAngle + diff / 21 * angleIndex).toFloat()
        } else {
            if (beginRotate) {
                lastAngle = -getAngle(current.dpValue, next.dpValue)
            }
            beginRotate = false
            angleIndex = 0
            null
        }
    }

    /**
     * 根据两点算取图标转的角度
     */
    private fun getAngle(fromPoint: PointD, toPoint: PointD): Double {
        val slope = getSlope(fromPoint, toPoint)
        if (slope == java.lang.Double.MAX_VALUE) {
            return if (toPoint.y > fromPoint.y) {
                0.0
            } else {
                180.0
            }
        } else if (slope == 0.0) {
            return if (toPoint.x > fromPoint.x) {
                (-90).toDouble()
            } else {
                90.0
            }
        }
        var deltaAngle = 0f
        if ((toPoint.y - fromPoint.y) * slope < 0) {
            deltaAngle = 180f
        }
        val radio = atan(slope)
        return 180 * (radio / Math.PI) + deltaAngle - 90
    }

    /**
     * 算斜率
     */
    private fun getSlope(fromPoint: PointD, toPoint: PointD): Double {
        return if (toPoint.x == fromPoint.x) {
            Double.MAX_VALUE
        } else (toPoint.y - fromPoint.y) / (toPoint.x - fromPoint.x)
    }
}