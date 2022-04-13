package com.example.mapdemo

import com.amap.api.maps.model.LatLng
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

object LonLatMercatorConverter {
    //经纬度转墨卡托
    fun lonLat2Mercator(lonLat: LatLng): PointD {
        val x = lonLat.longitude * 20037508.34 / 180
        var y = ln(tan((90 + lonLat.latitude) * Math.PI / 360)) / (Math.PI / 180)
        y = y * 20037508.34 / 180
        return PointD(x, y)
    }

    //墨卡托转经纬度
    fun mercator2lonLat(mercator: PointD): LatLng {
        val x = mercator.x / 20037508.34 * 180
        var y = mercator.y / 20037508.34 * 180
        y = 180 / Math.PI * (2 * atan(exp(y * Math.PI / 180)) - Math.PI / 2)
        return LatLng(y, x)
    }
}