package com.example.mapdemo

data class PointD(val x: Double, val y: Double)

fun PointD.interpolate(pointD: PointD, ratio: Double): PointD {
    return GeometryHelper.getPoint(this, pointD, ratio)
}

fun PointD.lengthTo(pointD: PointD): Double {
    return Vector2D(this, pointD).length
}