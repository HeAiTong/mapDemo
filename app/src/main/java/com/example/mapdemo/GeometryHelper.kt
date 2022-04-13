package com.example.mapdemo

object GeometryHelper {
    /// 0 ~> 360, atan2可以处理 90度 和 -90度 的case
    fun calAngle(point1: PointD, point2: PointD): Double {
        return Vector2D(point2.x - point1.x, point2.y - point1.y).angle
    }

    // return value: [-360, 360] 从向量vector到vector1方向的角度
    fun calAngle(vector1: Vector2D, vector2: Vector2D): Double {
        return vector2.angle - vector1.angle
    }

    fun getPoint(point1: PointD, point2: PointD, ratio: Double): PointD {
        val vector = Vector2D(point2.x - point1.x, point2.y - point1.y)
        return PointD(point1.x + ratio * vector.dx, point1.y + ratio * vector.dy)
    }
}