package com.example.airpollutionapp


import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.airpollutionapp.models.Station
import com.example.airpollutionapp.models.WindInstance
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.collections.CircleManager
import com.google.maps.android.collections.MarkerManager
import com.google.maps.android.collections.PolygonManager
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.ui.IconGenerator
import org.json.JSONException
import java.io.IOException
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


class MapsFragment : Fragment() {

    private var listStations:MutableList<Station> = mutableListOf()
    private var componentKey:String? = null

    private val callback = OnMapReadyCallback { googleMap ->

        val areaList = arrayListOf("Uka","Oskemen","Semey","Jarmin","Glub","Borod", "Beskara", "Ayagoz",
        "Altai", "Abai", "Katon", "Kokpek", "Kurshim", "Shaman", "Tarb", "Ulan", "Uzhar")

        for (area in areaList){
            drawPolygonOfArea(googleMap, area)
        }

        val centerLatLng = LatLng(48.6130209,81.7489928)

        val markerManager = MarkerManager(googleMap)
        val circleManager = CircleManager(googleMap)
        val polygonManager = PolygonManager(googleMap)

        markerManager.newCollection("marks")
        circleManager.newCollection("circle")
        polygonManager.newCollection("poly")

        val listStationOnMap = mutableListOf<StationOnMap>()

        for (station in listStations) {
            listStationOnMap.add(StationOnMap(station))
        }

        googleMap.setOnCameraMoveListener {
            //Log.i("tagC", "Camera move: ${googleMap.projection.visibleRegion.latLngBounds}")
           // Handler().postDelayed({
                googleMap.projection.visibleRegion.apply {
                    val polygonOptCamera = PolygonOptions().addAll(listOf(nearLeft, nearRight, farRight, farLeft))
                    // Handler().postDelayed({
                    for (stationOnMap in listStationOnMap) {
                        val currStation = stationOnMap.station
                        val latLngCenter = LatLng(currStation.latitude, currStation.longitude)

                        if (!containsPointOnCamera(latLngCenter, polygonOptCamera.points)){
                            //delete
                            if (stationOnMap.status == "add"){
                                stationOnMap.apply {
                                    markerManager.getCollection("marks").remove(marker)
                                    circle?.let { circleManager.getCollection("circle").remove(it)}
                                    polygon?.let { polygonManager.getCollection("poly").remove(it)}
                                    stationOnMap.status = "del"
                                }
                            }
                        } else {
                            //add
                            if (stationOnMap.status == "no-add" || stationOnMap.status == "del"){

                                val markerOpt = MarkerOptions().position(latLngCenter)
                                    .title("${currStation.name} (${currStation.address}) "+
                                            "${componentKey?.uppercase(Locale.getDefault())}:${currStation.components[componentKey]}")
                                stationOnMap.marker = markerManager.getCollection("marks").addMarker(markerOpt)

                                stationOnMap.status = "add"

                                if (WindInstance.speed <= 2) {
                                    val circleOpt = drawCircle(latLngCenter,currStation)
                                   stationOnMap.circle = circleManager.getCollection("circle").addCircle(circleOpt)
                                }
                                else {
                                    val polyOpt = drawEllipse(latLngCenter,currStation)
                                  stationOnMap.polygon = polygonManager.getCollection("poly").addPolygon(polyOpt)
                                }

                            }
                        }
                    }

                    markerManager.getCollection("marks").showAll()
                    circleManager.getCollection("circle").showAll()
                    polygonManager.getCollection("poly").showAll()
                    // }, 1000)
                }
           // }, 3500)
        }

        val latLngBounds = LatLngBounds(LatLng(45.321254,76.245117), LatLng(52.038977,85.979004))
        googleMap.setLatLngBoundsForCameraTarget(latLngBounds)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerLatLng,7.0f))
    }

    private fun containsPointOnCamera(p: LatLng, points: List<LatLng>): Boolean {
        val latLngBounds = LatLngBounds.Builder()
        for (point in points){
            latLngBounds.include(point)
        }
        return latLngBounds.build().contains(p)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        componentKey = arguments?.getString("component")
        val list = arguments?.getSerializable("stations") as MutableList<Station>?

        if (list!=null){
            listStations.addAll(list)
           // showToast("${list.size}")
        } else {
            showToast("list on map is null!")
        }
        //showToast("$componentKey")
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)
    }

    private fun drawCircle(point:LatLng,station: Station, radius:Double = 2500.0):CircleOptions{
        val circleOptions = CircleOptions()
        circleOptions.center(point)
        circleOptions.radius(radius)
        circleOptions.strokeColor(Color.BLACK)
        circleOptions.strokeWidth(0.0f)

        val strColor = getColorOfPollution(station.components)[componentKey]

        strColor?.let { circleOptions.fillColor(getColorOfName(it)) }

       // Log.i("test-tag","$componentKey ${station.address} station:${station.components} st:$color")

        return circleOptions
    }

    private fun drawPolygonOfArea(googleMap: GoogleMap, areaName:String) {
        try{

            val  geoJsonLayer:GeoJsonLayer = when(areaName) {
                "Oskemen" -> {
                     GeoJsonLayer(googleMap,R.raw.oskemen,context)
                }
                "Semey" -> {
                    GeoJsonLayer(googleMap,R.raw.semey,context)
                }
                "Uka" -> {
                    GeoJsonLayer(googleMap,R.raw.uka,context)
                }

                "Abai" -> {
                    GeoJsonLayer(googleMap,R.raw.abai,context)
                }
                "Altai" -> {
                    GeoJsonLayer(googleMap,R.raw.altai,context)
                }
                "Ayagoz" -> {
                    GeoJsonLayer(googleMap,R.raw.ayagoz,context)
                }
                "Beskara" -> {
                    GeoJsonLayer(googleMap,R.raw.beskaragai,context)
                }
                "Borod" -> {
                    GeoJsonLayer(googleMap,R.raw.borod,context)
                }
                "Glub" -> {
                    GeoJsonLayer(googleMap,R.raw.glubokoe,context)
                }
                "Jarmin" -> {
                    GeoJsonLayer(googleMap,R.raw.jarmin,context)
                }
                "Katon" -> {
                    GeoJsonLayer(googleMap,R.raw.katon,context)
                }
                "Kokpek" -> {
                    GeoJsonLayer(googleMap,R.raw.kokpekti,context)
                }
                "Kurshim" -> {
                    GeoJsonLayer(googleMap,R.raw.kurshim,context)
                }
                "Shaman" -> {
                    GeoJsonLayer(googleMap,R.raw.shamanai,context)
                }
                "Tarb" -> {
                    GeoJsonLayer(googleMap,R.raw.tarbagatay,context)
                }
                "Ulan" -> {
                    GeoJsonLayer(googleMap,R.raw.ulan,context)
                }
                "Uzhar" -> {
                    GeoJsonLayer(googleMap,R.raw.uzhar,context)
                }

                else -> {
                    GeoJsonLayer(googleMap,R.raw.oskemen,context)
                }
            }

            val stylePoly = geoJsonLayer.defaultPolygonStyle

            when(areaName) {
                /*"Semey" -> {
                    stylePoly.fillColor = Color.argb(50,101,66,255)
                    stylePoly.strokeWidth = 1.0f
                }*/
                "Oskemen" -> {
                    stylePoly.fillColor = Color.argb(70,66,196,255)
                    stylePoly.strokeWidth = 1.0f
                }
                "Uka" -> {
                    stylePoly.strokeColor = Color.argb(90,255,120,66)
                    stylePoly.strokeWidth = 3.0f
                }
                else -> {
                    stylePoly.strokeColor = Color.argb(100,76,0,153)
                    stylePoly.strokeWidth = 6.0f

                    val icg = IconGenerator(context)
                    icg.setColor(Color.GREEN)
                    icg.setTextAppearance(R.color.white)
                    val bitmap = icg.makeIcon(getNameArea(areaName))

                    googleMap.addMarker(
                        MarkerOptions().position(getCoordinateAreaName(areaName))
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    )
                }
            }

            geoJsonLayer.addLayerToMap()

        } catch (e:IOException) {
            Log.e("tag","${e.message}")
            e.printStackTrace()
        } catch (e:JSONException) {
            Log.e("tag","${e.message}")
            e.printStackTrace()
        }
    }

    private fun drawEllipse(centerLatLng: LatLng, station: Station):PolygonOptions{
        val options = PolygonOptions()
        val numPoints = 100
        var semiHorizontalAxis:Float
        var semiVerticalAxis: Float

        var dLatLng = 0.012
        var dAxis = 1.0

        if (WindInstance.speed >= 5 ){
            dLatLng*= (WindInstance.speed/2)*0.5
            dAxis = (WindInstance.speed/2)*0.5
        }

        when(getWindWithDegrees()){
            "С", "Ю" -> {
                semiHorizontalAxis = (0.02f * dAxis).toFloat()
                semiVerticalAxis = (0.02f *dAxis).toFloat()
                //dLatLng = 0.012
            }
            "З","В" -> {
                semiHorizontalAxis = (0.03f * dAxis).toFloat()
                semiVerticalAxis = (0.015f * dAxis).toFloat()
            }
            else -> {
                semiHorizontalAxis = (0.03f * dAxis).toFloat()
                semiVerticalAxis = (0.015f * dAxis).toFloat()
            }
        }

       // semiHorizontalAxis = 0.02f
        //semiVerticalAxis = 0.02f

        val phase = 2 * PI / numPoints

        //Log.i("tagEllipse", "phase:$phase point:$centerLatLng")


        for (i in 0..numPoints) {

            var pLat = 0.0
            var pLon = 0.0

                when(getWindWithDegrees()){
                    "З" -> {
                        pLat = (centerLatLng.latitude) + semiVerticalAxis * sin(i * phase)
                        pLon = (centerLatLng.longitude-dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "ЮЗ"-> {
                        pLat = (centerLatLng.latitude-(dLatLng/4)) + semiVerticalAxis * sin(i * phase+0.45)
                        pLon = (centerLatLng.longitude-dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "СЗ" -> {
                        pLat = (centerLatLng.latitude+(dLatLng/4)) + semiVerticalAxis * sin(i * phase-0.45)
                        pLon = (centerLatLng.longitude-dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "В" -> {
                        pLat = (centerLatLng.latitude) + semiVerticalAxis * sin(i * phase)
                        pLon = (centerLatLng.longitude+dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "ЮВ"-> {
                        pLat = (centerLatLng.latitude-(dLatLng/4)) + semiVerticalAxis * sin(i * phase-0.45)
                        pLon = (centerLatLng.longitude+dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "СВ" -> {
                        pLat = (centerLatLng.latitude+(dLatLng/4)) + semiVerticalAxis * sin(i * phase+0.45)
                        pLon = (centerLatLng.longitude+dLatLng) + semiHorizontalAxis * cos(i * phase)
                    }

                    "С"->{
                         pLat = (centerLatLng.latitude+dLatLng) + semiVerticalAxis * sin(i * phase)
                         pLon = (centerLatLng.longitude) + semiHorizontalAxis * cos(i * phase)
                    }

                    "Ю"->{
                        pLat = (centerLatLng.latitude-dLatLng) + semiVerticalAxis * sin(i * phase)
                        pLon = (centerLatLng.longitude) + semiHorizontalAxis * cos(i * phase)
                    }

                }

            //Log.i("tagEllipse", "i:$i lat:${centerLatLng.latitude} lon:${centerLatLng.longitude}")

            //googleMap.addMarker(MarkerOptions().position(LatLng(pLat,pLon)).title("lat:${abs(point.latitude-pLat)} lon:${abs(point.longitude-pLon)}"))
            //googleMap.addMarker(MarkerOptions().position(LatLng(pLat,pLon)).title("${LatLng(pLat,pLon)}"))

            options.add(
                LatLng(pLat,pLon)
            )
        }

        val strColor = getColorOfPollution(station.components)[componentKey]
        strColor?.let { options.fillColor(getColorOfName(it)) }

        return options.strokeWidth(0f)
    }

   data class StationOnMap(
       val station: Station,
       var status:String="no-add",
       var marker: Marker?=null,
       var polygon: Polygon?=null,
       var circle: Circle?=null
   )

}