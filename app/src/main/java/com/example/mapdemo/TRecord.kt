package com.example.mapdemo

import com.amap.api.maps.model.LatLng

data class TRecord(
    val distance: Double,
    val latLng: LatLng,
    val splitIndex: Int,
    val frameIndex: Int = -1
) {
    val isOriginal get() = frameIndex == -1
    var camera: CameraPosition? = null
}

data class CameraPosition(
    val currentIndex: Int,
    val pointD: PointD,
    val rotation: Float?
)

val CameraPosition.toLatLng: LatLng
    get() = LonLatMercatorConverter.mercator2lonLat(pointD)