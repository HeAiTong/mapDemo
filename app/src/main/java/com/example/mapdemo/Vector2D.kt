package com.example.mapdemo

import kotlin.math.atan2
import kotlin.math.sqrt

class Vector2D(val dx: Double, val dy: Double) {

    constructor(point1: PointD, point2: PointD) : this(point2.x - point1.x, point2.y - point1.y)

}

val Vector2D.length: Double
    get() = sqrt(dx * dx + dy * dy)


val Vector2D.angle: Double
    get() = atan2(dy, dx) / Math.PI * 180.0