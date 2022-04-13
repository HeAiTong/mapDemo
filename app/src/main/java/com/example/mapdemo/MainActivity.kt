package com.example.mapdemo

import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.*
import com.example.mapdemo.databinding.ActivityMainBinding
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.math.floor
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private val mapView get() = viewBinding.mapView
    private lateinit var mapAnimationHelper: MapAnimationHelper
    private val trackData get() = mapAnimationHelper.tRecords
    private var countDownTimer: CountDownTimer? = null
    private val delayHandler by lazy { Handler(Looper.getMainLooper()) }
    private val keyFrame = 40
    private val transitionDuration = 1500L
    private var isPlaying = false
    private val trackPaddingLeft: Int
        get() {
            return (resources.displayMetrics.density * 40 + 0.5f).toInt()
        }
    private val trackPaddingTop: Int
        get() {
            return (resources.displayMetrics.density * 100 + 0.5f).toInt()
        }
    private val trackPaddingRight: Int
        get() {
            return (resources.displayMetrics.density * 40 + 0.5f).toInt()
        }
    private val trackPaddingBottom: Int
        get() {
            return (resources.displayMetrics.density * 100 + 0.5f).toInt()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        mapView.onCreate(savedInstanceState)

        val recordList =
            ObjectMapper().apply {
                configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )
            }
                .readValue(assets.open("data.json"), object : TypeReference<List<RecordDto>>() {})
        mapAnimationHelper =
            MapAnimationHelper(listOf(recordList), keyFrame)

        Handler(Looper.getMainLooper()).postDelayed({
            showCompleteTrack(getLastFrameData())

            startAnimation(
                (mapAnimationHelper.trackDuration * 1000).toLong(),
                mapAnimationHelper.trackFrameCount
            )
        }, 1000)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    private fun showCompleteTrack(tRecords: List<List<TRecord>>) {
        transToNormalStatus(tRecords, false)
        val records = tRecords.map { list -> list.map { it.latLng } }
        drawVideoTrack(records, true)
    }

    private fun startAnimation(trackDuration: Long, trackFrameCount: Int) {
        isPlaying = true
        showVideoStart()
        transToStartPoint(
            getLastFrameData(),
            mapAnimationHelper.cameraRouteHelper.getStartPosition() ?: return
        )
        delayHandler.postDelayed({
            val period = (1000.0 / keyFrame).roundToLong()
            countDownTimer = object : CountDownTimer(trackDuration, period) {
                override fun onTick(millisUntilFinished: Long) {
                    val ratio = (trackDuration - millisUntilFinished) / trackDuration.toDouble()
                    val frameIndex = floor(ratio * trackFrameCount).toInt()
                    if (frameIndex <= 0) return
                    onFrameTick(frameIndex, trackFrameCount)
                }

                override fun onFinish() {
                    onFrameTick(trackFrameCount, trackFrameCount)
                    transToNormalStatus(getLastFrameData(), true)
                }
            }
            countDownTimer?.start()
        }, transitionDuration)
    }

    private fun onFrameTick(currentFrame: Int, frameCount: Int) {
        val splitTRecord = getKeyFrameData(currentFrame)
        showFrameData(
            currentFrame,
            frameCount,
            splitTRecord
        )
    }

    private fun showFrameData(
        currentFrame: Int,
        frameCount: Int,
        tRecords: List<List<TRecord>>
    ) {
        val lastRecord = tRecords.last().last()
        val records = tRecords.map { list -> list.map { it.latLng } }
        drawVideoTrack(records, currentFrame == frameCount)

        lastRecord.camera?.let {
            val mapStatus = MapStatus.Builder().rotate(it.rotation).target(it.toLatLng).build()
            val duration = floor(1000.0 / keyFrame).toInt()

            animateMapStatus(mapStatus, duration)
        }
    }

    private fun transToNormalStatus(tRecords: List<List<TRecord>>, animated: Boolean) {
        val latLngList = tRecords.flatMap { list -> list.map { it.latLng } }
        val viewWidth = viewBinding.mapView.width - trackPaddingLeft - trackPaddingRight
        val viewHeight = viewBinding.mapView.height - trackPaddingTop - trackPaddingBottom

        val newCenterX = viewWidth / 2 + trackPaddingLeft
        val newCenterY = viewHeight / 2 + trackPaddingTop
        val status = MapStatus.Builder()
            .targetScreen(Point(newCenterX, newCenterY))
            .overlook(0f)
            .rotate(0f)
            .build()
        val duration = if (animated) {
            transitionDuration.toInt()
        } else {
            0
        }

        animateBoundsAndMapStatus(
            latLngList,
            viewWidth,
            viewHeight,
            status,
            duration
        )
    }

    private fun getLastFrameData(): List<List<TRecord>> {
        mapAnimationHelper.trackFrameCount.let {
            return getKeyFrameData(it)
        }
    }

    //frameIndex from 1 .. frame count
    private fun getKeyFrameData(frameIndex: Int): List<List<TRecord>> {
        val splitIndex = mapAnimationHelper.splitIndexes
        val subIndex = trackData.indexOfFirst { frameIndex == it.frameIndex }
        val subRecords = trackData.subList(0, subIndex + 1)

        val result = subRecords.asSequence()
            .filter { it.isOriginal }
            .withIndex().groupBy {
                var group = -1
                for (index: Int in splitIndex) {
                    if (it.index < index) {
                        group = index
                        break
                    }
                }
                group
            }.map { group ->
                group.value.map { it.value }.toMutableList()
            }.toMutableList()
        if (result.isEmpty()) {
            result.add(arrayListOf(subRecords.last()))
        } else {
            result.last().add(subRecords.last())
        }
        return result
    }

    private fun transToStartPoint(tRecords: List<List<TRecord>>, cameraPosition: CameraPosition) {
        val latLngList = tRecords.flatMap { list -> list.map { it.latLng } }
        val padding = (resources.displayMetrics.density * 30 + 0.5f).toInt()
        val viewWidth = viewBinding.mapView.width - 2 * padding
        val viewHeight = viewBinding.mapView.height - 2 * padding

        val overlook = -45f
        val status = MapStatus.Builder().target(cameraPosition.toLatLng)
            .targetScreen(
                Point(
                    viewBinding.mapView.width / 2,
                    viewBinding.mapView.height / 2
                )
            )
            .overlook(overlook)
            .rotate(cameraPosition.rotation)
            .build()

        animateBoundsAndMapStatus(
            latLngList,
            viewWidth,
            viewHeight,
            status,
            transitionDuration.toInt()
        )
    }

    //map - start
    private var videoLines = ArrayList<Polyline>()
    private var videoStart: Marker? = null
    private var videoEnd: Marker? = null

    @DrawableRes
    private var startDrawable = R.drawable.history_icon_map_start

    @DrawableRes
    private var endDrawable = R.drawable.history_icon_map_stop

    private fun animateBoundsAndMapStatus(
        points: List<LatLng>,
        width: Int,
        height: Int,
        mapStatus: MapStatus,
        aDuration: Int
    ) {
        if (points.isEmpty()) return
        val convertedPoints = points.map {
            val destPoint = coordinateConvert(LatLng(it.latitude, it.longitude))
            LatLng(destPoint.latitude, destPoint.longitude)
        }
        val rect = getBoundsByLatLngList(convertedPoints)
        val maxLatLng = LatLng(rect[0], rect[1])
        val minLatLng = LatLng(rect[2], rect[3])
        val latLngBounds = LatLngBounds.Builder().include(maxLatLng).include(minLatLng).build()
        val boundsStatus = CameraUpdateFactory.newLatLngBounds(latLngBounds, width, height, 0)

        mapView.map?.addOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(cameraPosition: com.amap.api.maps.model.CameraPosition) =
                Unit

            override fun onCameraChangeFinish(cameraPosition: com.amap.api.maps.model.CameraPosition) {
                mapView.map?.removeOnCameraChangeListener(this)
                val builder = com.amap.api.maps.model.CameraPosition.builder()
                    .tilt(mapStatus.overlook ?: cameraPosition.tilt)
                    .bearing(mapStatus.rotate ?: cameraPosition.bearing)
                    .zoom(cameraPosition.zoom)

                if (mapStatus.targetScreen != null) {
                    val latLng =
                        mapView.map!!.projection.fromScreenLocation(mapStatus.targetScreen)
                    val center = LatLng(
                        2 * latLngBounds.getCenter().latitude - latLng.latitude,
                        2 * latLngBounds.getCenter().longitude - latLng.longitude
                    )
                    builder.target(center)
                }
                if (mapStatus.target != null) {
                    builder.target(
                        coordinateConvert(mapStatus.target)
                    )
                }
                if (aDuration == 0) {
                    mapView.map?.moveCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
                } else {
                    mapView.map?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(builder.build()),
                        floor(aDuration / 3.0 * 1.5).toLong(),
                        null
                    )
                }
            }
        })
        if (aDuration == 0) {
            mapView.map?.moveCamera(boundsStatus)
        } else {
            mapView.map?.animateCamera(boundsStatus, floor(aDuration / 3.0 * 1.0).toLong(), null)
        }
    }

    private fun animateMapStatus(mapStatus: MapStatus, duration: Int) {
        val builder = com.amap.api.maps.model.CameraPosition.Builder()
        if (mapStatus.target != null) {
            builder.target(coordinateConvert(mapStatus.target))
        } else {
            builder.target(mapView.map?.cameraPosition?.target)
        }
        if (mapStatus.overlook != null) {
            builder.tilt(mapStatus.overlook)
        } else {
            mapView.map?.cameraPosition?.tilt?.let { builder.tilt(it) }
        }
        if (mapStatus.rotate != null) {
            builder.bearing(mapStatus.rotate)
        } else {
            mapView.map?.cameraPosition?.bearing?.let { builder.bearing(it) }
        }
        mapView.map?.cameraPosition?.zoom?.let { builder.zoom(it) }
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(builder.build())
        mapView.map?.moveCamera(cameraUpdate)
//        mapView.map?.animateCamera(cameraUpdate, 1.toLong(),object :AMap.CancelableCallback{
//            override fun onFinish() {
//                Log.e("gaode","finish")
//            }
//
//            override fun onCancel() {
//                Log.e("gaode","cancel")
//            }
//        })
    }

    private fun drawVideoTrack(splitPoints: List<List<LatLng>>, isComplete: Boolean) {
        for (i in splitPoints.indices) {
            //update last polyline
            if (!isComplete && i != splitPoints.lastIndex) continue

            val mapPoints = splitPoints[i]
            val latLngList = ArrayList<LatLng>()
            mapPoints.forEach {
                val destPoint = coordinateConvert(LatLng(it.latitude, it.longitude))
                latLngList.add(destPoint)
            }

            if (latLngList.size == 0 || latLngList.size == 1) continue

            if (videoLines.getOrNull(i) != null) {
                videoLines[i].points = latLngList
            } else {
                val polylineOptions = PolylineOptions()
                    .addAll(latLngList)
                    .color(Color.RED)
                    .lineJoinType(PolylineOptions.LineJoinType.LineJoinRound)
                    .lineCapType(PolylineOptions.LineCapType.LineCapRound)
                    .width(10f)
                mapView.map?.addPolyline(polylineOptions)?.let {
                    videoLines.add(it)
                }
            }
        }

        val start = videoLines.firstOrNull()?.points?.firstOrNull()
        val end = if (videoLines.lastOrNull()?.points?.count() ?: 0 > 1) {
            videoLines.last().points.last()
        } else {
            null
        }
        if (videoStart == null && start != null) {
            val startLatLng = LatLng(start.latitude, start.longitude)
            val startMarker = MarkerOptions()
                .position(startLatLng)
                .icon(BitmapDescriptorFactory.fromResource(startDrawable))
            videoStart = mapView.map?.addMarker(startMarker)?.apply { isClickable = false }
        }
        if (end != null) {
            val endLatLng = LatLng(end.latitude, end.longitude)
            if (videoEnd != null) {
                if (isComplete) {
                    videoEnd?.setIcon(BitmapDescriptorFactory.fromResource(endDrawable))
                    videoEnd?.setAnchor(0.5f, 1f)
                } else {
                    videoEnd?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.history_icon_route_sign))
                    videoEnd?.setAnchor(0.5f, 0.5f)
                }
                videoEnd?.position = endLatLng
            } else {
                val endMarker = MarkerOptions()
                if (isComplete) {
                    endMarker.icon(BitmapDescriptorFactory.fromResource(endDrawable))
                } else {
                    endMarker.icon(BitmapDescriptorFactory.fromResource(R.drawable.history_icon_route_sign))
                }
                endMarker.position(endLatLng)
                videoEnd = mapView.map?.addMarker(endMarker)?.apply { isClickable = false }
            }
        }
    }

    private fun showVideoStart() {
        if (videoEnd != null) {
            videoEnd?.remove()
            videoEnd = null
        }
        if (videoLines.isNotEmpty()) {
            for (polyline in videoLines) {
                polyline.remove()
            }
            videoLines.clear()
        }
    }

    private fun coordinateConvert(latLng: LatLng): LatLng {
        return CoordinateConverter(this).from(CoordinateConverter.CoordType.GPS).coord(latLng)
            .convert()
    }

    private fun getBoundsByLatLngList(latLngList: List<LatLng>): DoubleArray {
        val rect = DoubleArray(4)
        var maxX = -1000.0
        var maxY = -1000.0
        var minX = 1000.0
        var minY = 1000.0
        for (latLng in latLngList) {
            if (maxX < latLng.longitude) {
                maxX = latLng.longitude
            }
            if (minX > latLng.longitude) {
                minX = latLng.longitude
            }
            if (maxY < latLng.latitude) {
                maxY = latLng.latitude
            }
            if (minY > latLng.latitude) {
                minY = latLng.latitude
            }
        }
        rect[0] = maxY
        rect[1] = maxX
        rect[2] = minY
        rect[3] = minX
        return rect
    }

    private fun LatLngBounds.getCenter(): LatLng {
        val lat = (southwest.latitude + northeast.latitude) / 2.0
        val long = if (southwest.longitude <= northeast.longitude) {
            (northeast.longitude + southwest.longitude) / 2.0
        } else {
            (northeast.longitude + 360.0 + southwest.longitude) / 2.0
        }
        return LatLng(lat, long)
    }
}