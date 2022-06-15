package com.zacharee1.boredsigns.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.util.Utils
import com.zacharee1.boredsigns.widgets.WeatherForecastWidget
import com.zacharee1.boredsigns.widgets.WeatherWidget
import github.vatsal.easyweather.Helper.TempUnitConverter
import github.vatsal.easyweather.Helper.WeatherCallback
import github.vatsal.easyweather.WeatherMap
import github.vatsal.easyweather.retrofit.models.*
import github.vatsal.easyweather.retrofit.models.List
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import org.json.simple.JSONValue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class WeatherService : Service() {
    companion object {
        const val ACTION_UPDATE_WEATHER = "com.zacharee1.boredsigns.action.UPDATE_WEATHER"
        const val API_KEY = "" //IMPORTANT: Use your own OWM API key here when building for yourself!

        const val EXTRA_TEMP = "temp"
        const val EXTRA_TEMP_EX = "temp_ex"
        const val EXTRA_LOC = "loc"
        const val EXTRA_DESC = "desc"
        const val EXTRA_ICON = "icon"
        const val EXTRA_TIME = "time"

        const val WHICH_UNIT = "weather_unit"
    }

    private var useCelsius: Boolean = true
    private lateinit var prefs: SharedPreferences

    private var locClient: FusedLocationProviderClient? = null
    private lateinit var alarmManager: AlarmManager

    private lateinit var alarmIntent: PendingIntent

    private val locReq: LocationRequest = LocationRequest.create().setSmallestDisplacement(300F)
    private val locCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            for (location in p0.locations) {
                onHandleIntent(ACTION_UPDATE_WEATHER)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            onHandleIntent(p1?.action)
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onHandleIntent(intent?.action)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()

        startForeground()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, this::class.java)
        intent.action = ACTION_UPDATE_WEATHER
        alarmIntent = PendingIntent.getService(this, 0, intent, 0)

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 7200 * 1000,
                7200 * 1000,
                alarmIntent)

        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locClient = LocationServices.getFusedLocationProviderClient(this)

            val locMan = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = when {
                locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }
            locMan.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(p0: Location) {
                    TODO("Not yet implemented")
                }

                override fun onLocationChanged(locations: MutableList<Location>) {
                    onHandleIntent(ACTION_UPDATE_WEATHER)
                }

                override fun onProviderDisabled(provider: String) {

                }

                override fun onProviderEnabled(provider: String) {

                }

                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {

                }
            }, null)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(ACTION_UPDATE_WEATHER))
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        stopLocationUpdates()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        alarmManager.cancel(alarmIntent)
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(NotificationChannel("weather",
                    resources.getString(R.string.weather_widget_title), NotificationManager.IMPORTANCE_LOW))
        }

        startForeground(1337,
                NotificationCompat.Builder(this, "weather")
                        .setSmallIcon(R.mipmap.ic_launcher_boredsigns)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .build())
    }

    private fun onHandleIntent(action: String?) {
        when (action) {
            ACTION_UPDATE_WEATHER -> {
                useCelsius = prefs.getBoolean(WHICH_UNIT, true)
                val locMan = getSystemService(Context.LOCATION_SERVICE) as LocationManager

                if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    if ((!locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)
                                    && !locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                            || !prefs.getBoolean("use_location", true)) {
                        locClient?.locationAvailability?.addOnCompleteListener {
                            if (it.result.isLocationAvailable) {
                                getCurrentLocWeather()
                            } else {
                                getSavedLocWeather()
                            }
                        }
                    } else {
                        getCurrentLocWeather()
                    }
                } else {
                    getSavedLocWeather()
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun getCurrentLocWeather() {
        try {
            locClient?.lastLocation?.addOnCompleteListener {
                it.result?.let {
                    val lat = it.latitude
                    val lon = it.longitude
                    getWeather(lat, lon)
                }
            }
        } catch (e: SecurityException) {
            getSavedLocWeather()
        } catch (e: ApiException) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()

            val bundle = Bundle()
            bundle.putString("message", e.localizedMessage)

            FirebaseAnalytics.getInstance(this)
                    .logEvent("ApiException", bundle)
        }
    }

    private fun getSavedLocWeather() {
        val loc = getSavedLoc()
        getWeather(loc.lat, loc.lon)
    }

    private fun getSavedLoc(): Loc {
        val lat = prefs.getFloat("weather_lat", 51.508530F).toDouble()
        val lon = prefs.getFloat("weather_lon", -0.076132F).toDouble()
        return Loc(lat, lon)
    }

    private fun getWeather(lat: Double, lon: Double) {
        try {
            val geo = Geocoder(applicationContext, Locale.getDefault())
            val weather = WeatherMap(applicationContext, API_KEY)
            val addrs = geo.getFromLocation(lat, lon, 1)

            if (isCurrentActivated()) {
                weather.getLocationWeather(lat.toString(), lon.toString(), object : WeatherCallback() {
                    @SuppressLint("CheckResult")
                    override fun success(response: WeatherResponseModel) {
                        try {
                            val extras = Bundle()

                            val temp = response.main.temp
                            val tempDouble: Double = if (useCelsius) TempUnitConverter.convertToCelsius(temp) else TempUnitConverter.convertToFahrenheit(temp)
                            val time = SimpleDateFormat("h:mm aa", Locale.getDefault()).format(Date(response.dt.toLong() * 1000))

                            val formatted = DecimalFormat("#").format(tempDouble).toString()

                            extras.putString(EXTRA_TEMP, "${formatted}° ${if (useCelsius) "C" else "F"}")
                            extras.putString(EXTRA_LOC, "${addrs[0].locality}, ${addrs[0].adminArea}")
                            extras.putString(EXTRA_DESC, capitalize(response.weather[0].description))
                            extras.putString(EXTRA_TIME, time)
                            extras.putString(EXTRA_ICON, Utils.parseWeatherIconCode(response.weather[0].id, response.weather[0].icon))

                            Utils.sendWidgetUpdate(this@WeatherService, WeatherWidget::class.java, extras)
                        } catch (e: Exception) {
                            failure(e.localizedMessage)
                        }
                    }

                    override fun failure(error: String?) {
                        Toast.makeText(this@WeatherService, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), error), Toast.LENGTH_SHORT).show()
                    }
                })
            }

            if (isForecastActivated()) {
                ForecastParser().sendRequest(lat.toString(), lon.toString(), object : ForecastCallback {
                    @SuppressLint("CheckResult")
                    override fun onSuccess(model: ForecastResponseModel) {
                        val extras = Bundle()

                        val highTemps = ArrayList<String>()
                        val lowTemps = ArrayList<String>()
                        val times = ArrayList<String>()
                        val icons = ArrayList<String>()

                        model.list
                                .map { it.main.temp_max }
                                .map { if (useCelsius) TempUnitConverter.convertToCelsius(it) else TempUnitConverter.convertToFahrenheit(it) }
                                .map { DecimalFormat("#").format(it).toString() }
                                .mapTo(highTemps) { "$it° ${if (useCelsius) "C" else "F"}" }

                        model.list
                                .map { it.main.temp_min }
                                .map { if (useCelsius) TempUnitConverter.convertToCelsius(it) else TempUnitConverter.convertToFahrenheit(it) }
                                .map { DecimalFormat("#").format(it).toString() }
                                .mapTo(lowTemps) { "$it° ${if (useCelsius) "C" else "F"}" }

                        model.list.mapTo(times) { SimpleDateFormat("M/d", Locale.getDefault()).format(Date(it.dt.toLong() * 1000)) }

                        model.list.mapTo(icons) { Utils.parseWeatherIconCode(it.weather[0].id, it.weather[0].icon) }

                        extras.putStringArrayList(EXTRA_TEMP, highTemps)
                        extras.putStringArrayList(EXTRA_TEMP_EX, lowTemps)
                        extras.putString(EXTRA_LOC, "${addrs[0].locality}, ${addrs[0].adminArea}")
                        extras.putStringArrayList(EXTRA_TIME, times)
                        extras.putStringArrayList(EXTRA_ICON, icons)

                        Utils.sendWidgetUpdate(this@WeatherService, WeatherForecastWidget::class.java, extras)
                    }

                    override fun onFail(message: String) {
                        Toast.makeText(this@WeatherService, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), message), Toast.LENGTH_SHORT).show()
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val bundle = Bundle()
            bundle.putString("message", e.localizedMessage)
            bundle.putString("stacktrace", Arrays.toString(e.stackTrace))
            FirebaseAnalytics.getInstance(this).logEvent("failed_weather", bundle)
            Toast.makeText(this, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }

    private fun capitalize(string: String): String {
        val builder = StringBuilder()
        val words = string.split(" ")

        for (word in words) {
            if (builder.isNotEmpty()) {
                builder.append(" ")
            }

            builder.append(word[0].toUpperCase()).append(word.substring(1, word.length))
        }

        return builder.toString()
    }

    private fun startLocationUpdates() {
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locClient?.requestLocationUpdates(locReq, locCallback, null)
        }
    }

    private fun stopLocationUpdates() {
        locClient?.removeLocationUpdates(locCallback)
    }

    private fun isCurrentActivated(): Boolean {
        return Utils.isWidgetInUse(WeatherWidget::class.java, this)
    }

    private fun isForecastActivated(): Boolean {
        return Utils.isWidgetInUse(WeatherForecastWidget::class.java, this)
    }

    class ForecastParser {
        private val numToGet = 7
        private val template = "http://api.openweathermap.org/data/2.5/forecast/daily?lat=LAT&lon=LON&cnt=$numToGet&appid=$API_KEY"

        @SuppressLint("CheckResult")
        fun sendRequest(lat: String, lon: String, callback: ForecastCallback) {
            val req = template.replace("LAT", lat).replace("LON", lon)

            try {
                Observable.fromCallable {asyncGetJsonString(URL(req))}
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            if (it.has("cod") && it.getString("cod") != "200") {
                                callback.onFail(it.getString("message"))
                            } else {
                                try {
                                    callback.onSuccess(parseJsonData(it))
                                } catch (e: Exception) {
                                    callback.onFail(e.localizedMessage)
                                }
                            }
                        }
            } catch (e: Exception) {
                callback.onFail(e.localizedMessage)
            }
        }

        private fun parseJsonData(json: JSONObject): ForecastResponseModel {
            val response = ForecastResponseModel()
            val list = ArrayList<List>()

            val stuff = json.getJSONArray("list")

            for (i in 0 until stuff.length()) {
                val l = List()
                val main = Main()
                val weather = Weather()

                val s = stuff.getJSONObject(i)

                weather.icon = s.getJSONArray("weather").getJSONObject(0).getString("icon")
                weather.id = s.getJSONArray("weather").getJSONObject(0).getString("id")
                main.temp_max = s.getJSONObject("temp").getString("max")
                main.temp_min = s.getJSONObject("temp").getString("min")

                l.weather = arrayOf(weather)
                l.main = main
                l.dt = s.getString("dt")

                list.add(l)
            }

            list.removeAt(0)

            val listArr = arrayOfNulls<List>(list.size)
            response.list = list.toArray(listArr)

            return response
        }

        private fun asyncGetJsonString(url: URL): JSONObject{
            var connection = url.openConnection() as HttpURLConnection

            return try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || connection.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as HttpURLConnection
                    }
                }

                val input = if (connection.responseCode < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream

                input.use { _ ->
                    val reader = BufferedReader(InputStreamReader(input, Charset.forName("UTF-8")))

                    val text = StringBuilder()
                    var cp: Int

                    do {
                        cp = reader.read()
                        if (cp == -1) break

                        text.append(cp.toChar())
                    } while (true)

                    return JSONObject(text.toString())
                }
            } catch (e: Exception) {
                if ((e.cause != null && e.cause is UnknownHostException) || e is UnknownHostException) {
                    JSONObject("{\"cod\":001, \"message\": \"Unknown Host\"}")
                } else {
                    e.printStackTrace()
                    JSONObject("{\"cod\":001, \"message\": \"${JSONValue.escape(e.localizedMessage)}\"}")
                }
            }
        }
    }

    interface ForecastCallback {
        fun onSuccess(model: ForecastResponseModel)
        fun onFail(message: String)
    }

    class Loc(var lat: Double, var lon: Double)
}
