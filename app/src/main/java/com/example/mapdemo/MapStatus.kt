package com.example.mapdemo

import android.graphics.Point
import com.amap.api.maps.model.LatLng

class MapStatus internal constructor(
    @JvmField val rotate: Float?,
    @JvmField val target: LatLng?,
    @JvmField val overlook: Float?,
    @JvmField val targetScreen: Point?
) {
    class Builder {
        private var rotate: Float? = null
        private var target: LatLng? = null
        private var overlook: Float? = null
        private var targetScreen: Point? = null

        fun rotate(var1: Float?): Builder {
            rotate = var1
            return this
        }

        fun target(var1: LatLng?): Builder {
            target = var1
            return this
        }

        fun overlook(var1: Float): Builder {
            overlook = var1
            return this
        }

        fun targetScreen(var1: Point?): Builder {
            targetScreen = var1
            return this
        }

        fun build(): MapStatus {
            return MapStatus(rotate, target, overlook, targetScreen)
        }
    }
}