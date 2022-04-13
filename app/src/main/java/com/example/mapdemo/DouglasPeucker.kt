package com.example.mapdemo

import com.amap.api.maps.model.LatLng

object DouglasPeucker {
    fun douglasPeucker(points: List<DPPoint>, tolerance: Double): List<DPPoint> {
        if (points.size < 3) {
            return points
        }

        val firstIndex = 0
        val lastIndex = points.lastIndex
        val pointIndexesToKeep = arrayListOf<Int>()

        //Add the first and last index to the keepers
        pointIndexesToKeep.add(firstIndex)
        pointIndexesToKeep.add(lastIndex)

        douglasPeuckerReduction(points, firstIndex, lastIndex, tolerance, pointIndexesToKeep)
        pointIndexesToKeep.sort()

        return pointIndexesToKeep.map { points[it] }
    }

    /*
     *  计算点(point)的时间同步欧式距离
     */
    private fun perpendicularDistance(
        startPoint: DPPoint,
        endPoint: DPPoint,
        point: DPPoint
    ): Double {
        val duration = endPoint.dpKey - startPoint.dpKey
        if (duration == 0.0) {
            return (point.dpValue.lengthTo(startPoint.dpValue) +
                    point.dpValue.lengthTo(endPoint.dpValue)) / 2
        }

        val ratio = (point.dpKey - startPoint.dpKey) / duration
        val newPointValue = startPoint.dpValue.interpolate(endPoint.dpValue, ratio)
        return point.dpValue.lengthTo(newPointValue)
    }

    /*
    *  将要保留的点添加到pointIndexesToKeep中
    */
    private fun douglasPeuckerReduction(
        points: List<DPPoint>,
        firstPoint: Int,
        lastPoint: Int,
        tolerance: Double,
        pointIndexesToKeep: ArrayList<Int>
    ) {
        if (lastPoint <= firstPoint + 1) return

        var maxDistance = 0.0
        var maxDistanceIndex = 0

        for (index in firstPoint + 1 until lastPoint) {
            val distance =
                perpendicularDistance(points[firstPoint], points[lastPoint], points[index])
            if (distance > maxDistance) {
                maxDistance = distance
                maxDistanceIndex = index
            }
        }

        if (maxDistance > tolerance) {
            //Add the largest point that exceeds the tolerance
            pointIndexesToKeep.add(maxDistanceIndex)

            douglasPeuckerReduction(
                points,
                firstPoint,
                maxDistanceIndex,
                tolerance,
                pointIndexesToKeep
            )

            douglasPeuckerReduction(
                points,
                maxDistanceIndex,
                lastPoint,
                tolerance,
                pointIndexesToKeep
            )
        }
    }
}

data class DPPoint(val latLng: LatLng, val dpKey: Double) {
    val dpValue: PointD get() = LonLatMercatorConverter.lonLat2Mercator(latLng)
}