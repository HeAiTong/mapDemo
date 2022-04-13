package com.example.mapdemo

import android.graphics.RectF
import android.util.Log
import android.util.SizeF
import com.amap.api.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

class MapAnimationHelper(splitRecords: List<List<RecordDto>>, private val frameRate: Int) {
    private val recordPoints: List<List<DPPoint>>
    val splitIndexes: ArrayList<Int>

    val trackDuration: Double
    val trackFrameCount get() = ceil((trackDuration * frameRate)).toInt()
    val tRecords: List<TRecord>
    val cameraRouteHelper: CameraRouteHelper
    private var lastCameraIndex = 0

    init {
        val dpPoints = generateDPPoints(splitRecords)
        Log.d("animation", "points before dp: ${dpPoints.flatten().count()}")
        val bounds = calculateBoundingBox(dpPoints, 0.5f)
        val tolerance = minOf(bounds.width, bounds.height).toDouble() * 0.005
        val smallRecords = shrinkPoints(dpPoints, tolerance)
        Log.d("animation", "points after dp: ${smallRecords.flatten().count()}")

        recordPoints = smallRecords
        splitIndexes = getSplitIndexes(smallRecords)
        trackDuration = calculateDuration(smallRecords)
        cameraRouteHelper = initializeCameraHelper(smallRecords, trackDuration)
        tRecords = prepareTrackAnimationData()
    }

    private fun generateDPPoints(splitRecords: List<List<RecordDto>>): List<List<DPPoint>> {
        return splitRecords.map { records ->
            records.mapNotNull {
                if (it.c07 !== null && it.c02 != null && it.c03 != null) {
                    DPPoint(LatLng(it.c02!!, it.c03!!), it.c07!!.toDouble())
                } else {
                    null
                }
            }
        }
    }

    private fun shrinkPoints(
        recordPoints: List<List<DPPoint>>,
        tolerance: Double,
        targetCount: Int = 450,
        limitCount: Int = 5
    ): List<List<DPPoint>> {
        var count = recordPoints.flatten().count()
        Log.d("animation", "shrink start: $count")
        if (count <= targetCount) {
            return recordPoints
        }

        var records = recordPoints
        for (i in 1..limitCount) {
            val newTolerance = tolerance * i
            val tempRecords = arrayListOf<List<DPPoint>>()
            for (record in records) {
                val newRecord = DouglasPeucker.douglasPeucker(record, newTolerance)
                tempRecords.add(newRecord)
            }
            records = tempRecords
            count = records.flatten().count()
            Log.d("animation", "shrink process: $count")
            if (count <= targetCount) break
        }
        Log.d("animation", "shrink end: $count")
        return records
    }

    private fun calculateDuration(recordPoints: List<List<DPPoint>>): Double {
        var fullLen = 0.0
        for (points in recordPoints) {
            if (points.count() <= 1) continue
            for (i in 0 until points.lastIndex) {
                val len = points[i].dpValue.lengthTo(points[i + 1].dpValue)
                fullLen += len
            }
        }
        val sizeF = calculateBoundingBox(recordPoints)
        val perimeter = (sizeF.width + sizeF.height) * 2
        val ratio = minOf(sizeF.width, sizeF.height) / max(sizeF.width, sizeF.height)
        val constant = 0.5.pow(0.5)
        val correct = -(1 - constant) * ratio * ratio + 2 * (1 - constant) * ratio + constant
        val laps = fullLen / perimeter * correct
        return when {
            laps < 1 -> laps * 10 + 10
            laps < 4 -> (laps - 1) * 20 / 3 + 20
            laps < 10 -> (laps - 4) * 17 / 6 + 40
            else -> 57.0
        }
    }

    //get bounding box,if aspectRatio <= 0, return origin sie
    private fun calculateBoundingBox(points: List<List<DPPoint>>, aspectRatio: Float = 0f): SizeF {
        val rectF = getBounds(points.flatMap { list -> list.map { it.dpValue } })
        val originSize = SizeF(rectF.width(), rectF.height())
        return aspect(originSize, aspectRatio)
    }

    private fun aspect(originSize: SizeF, aspectRatio: Float): SizeF {
        var size = SizeF(max(100f, abs(originSize.width)), max(100f, abs(originSize.height)))
        if (aspectRatio <= 0) return size
        val ratio = size.width / size.height
        if (ratio > aspectRatio) {
            size = SizeF(size.width, size.width / aspectRatio)
        } else if (ratio < aspectRatio) {
            size = SizeF(size.height * aspectRatio, size.height)
        }
        return size
    }

    private fun getBounds(aPoints: List<PointD>): RectF {
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        aPoints.forEachIndexed { _, point ->
            if (maxX < point.x) {
                maxX = point.x
            }
            if (minX > point.x) {
                minX = point.x
            }
            if (maxY < point.y) {
                maxY = point.y
            }
            if (minY > point.y) {
                minY = point.y
            }
        }
        return RectF(minX.toFloat(), maxY.toFloat(), maxX.toFloat(), minY.toFloat())
    }

    private fun getCenter(points: List<List<DPPoint>>): PointD {
        val rectF = getBounds(points.flatMap { list -> list.map { it.dpValue } })
        return PointD(rectF.centerX().toDouble(), rectF.centerY().toDouble())
    }

    private fun getSplitIndexes(recordPoints: List<List<DPPoint>>): ArrayList<Int> {
        val splitIndexes = arrayListOf<Int>()
        if (recordPoints.count() <= 1) return splitIndexes
        var count = recordPoints.first().count()
        for (i in 1..recordPoints.lastIndex) {
            splitIndexes.add(count)
            count += recordPoints[i].count()
        }
        return splitIndexes
    }

    private fun initializeCameraHelper(
        recordPoints: List<List<DPPoint>>,
        videoLength: Double
    ): CameraRouteHelper {
        val bounds = calculateBoundingBox(recordPoints)
        val tolerance = (minOf(bounds.width, bounds.height)).toDouble() / 4
        val cameraPoints = shrinkPoints(listOf(recordPoints.flatten()), tolerance, 15, 3).first()
        Log.d("animation", "camera points: ${cameraPoints.count()}")
        return CameraRouteHelper(cameraPoints, getCenter(recordPoints), videoLength)
    }

    private fun prepareTrackAnimationData(): List<TRecord> {
        val originalRecords = recordPoints.flatten().mapIndexed { index, dpPoint ->
            val splitIndex = splitIndexes.findLast { it <= index } ?: 0
            TRecord(dpPoint.dpKey, dpPoint.latLng, splitIndex)
        }

        val data = arrayListOf<TRecord>()
        data.addAll(originalRecords)
        val interval = data.last().distance / trackFrameCount
        for (i in 1..trackFrameCount) {//insert frame data.json
            val distance = interval * i
            val start =
                originalRecords.findLast { it.distance < distance } ?: originalRecords.first()
            val next = originalRecords.find { it.distance >= distance } ?: originalRecords.last()
            val nextIndex = originalRecords.indexOf(next)
            val inSameSplit = !splitIndexes.contains(nextIndex)

            val ratio = if (start.distance == next.distance) {
                1.0
            } else {
                (distance - start.distance) / (next.distance - start.distance)
            }
            val insertLatLng = start.latLng.ratio(next.latLng, ratio)
            val cameraPosition = if (i == 1) {
                cameraRouteHelper.getStartPosition()
            } else {
                cameraRouteHelper.getPosition(distance, lastCameraIndex)
            }

            val insertData = if (inSameSplit) {
                TRecord(distance, insertLatLng, start.splitIndex, i)
            } else {
                TRecord(start.distance, start.latLng, start.splitIndex, i)
            }
            cameraPosition?.let {
                insertData.camera = it
                lastCameraIndex = it.currentIndex
            }

            val index = data.indexOfLast { it.distance <= distance }
            data.add(index + 1, insertData)
        }
        return data
    }

    private fun LatLng.ratio(other: LatLng, ratio: Double): LatLng {
        val lat = latitude + (other.latitude - latitude) * ratio
        val lon = longitude + (other.longitude - longitude) * ratio
        return LatLng(lat, lon)
    }
}