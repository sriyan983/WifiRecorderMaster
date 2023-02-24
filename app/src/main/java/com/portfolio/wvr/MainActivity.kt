package com.portfolio.wvr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.instacart.library.truetime.TrueTime
import com.portfolio.utils.InitTrueTimeAsyncTask
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.net.*
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private var PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var ip: String? =""
    private var trueTime: String? =""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initTrueTime(this)

        load()

        populateUI()

        CoroutineScope(Dispatchers.Default).launch { runServer() }
    }

    private fun populateUI() {
        var timeView = findViewById<TextView>(R.id.ntpTimeView)
        trueTime = getTrueTime().toString()
        timeView.text = trueTime

        var ipIpoutView = findViewById<TextView>(R.id.ipEditText)
        ipIpoutView.text = ip//getLocalIPAddress()
    }

    private fun load() {
        setContentView(R.layout.activity_main)
        var startRecordingButton = findViewById<Button>(R.id.startRecordingButton)
        startRecordingButton.setOnClickListener {
            startRecording()
        }

        /*var initTimeSyncButton = findViewById<Button>(R.id.initTimeSyncButton)
        initTimeSyncButton.setOnClickListener {

            populateUI()
            //Log.d("MainActivty", getTrueTime().toString())
        }*/

        // add the storage access permission request for Android 9 and below.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permissionList = PERMISSIONS_REQUIRED.toMutableList()
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissionList.add(Manifest.permission.ACCESS_NETWORK_STATE)
            PERMISSIONS_REQUIRED = permissionList.toTypedArray()
        }

        if (!hasPermissions(this)) {
            // Request camera-related permissions
            activityResultLauncher.launch(PERMISSIONS_REQUIRED)
        }
    }

    private fun startRecording() {
        var intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in PERMISSIONS_REQUIRED && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }

    /*
    Utility functions
     */
    fun showDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        //builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

        builder.setPositiveButton(android.R.string.yes) { dialog, which ->
            exitProcess(-1)
        }

        builder.show()
    }

    private suspend fun runServer() {
        val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
            .bind(InetSocketAddress(ip, 8889))
        while (true) {
            println("Server running: ${server.localAddress}")
            val socket = server.accept()
            println("Socket accepted: ${socket.remoteAddress}")
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)
            val line = input.readUTF8Line()
            println("Server received '$line' from ${socket.remoteAddress}")
            if (line.equals("wvrc-app-connect")) {
                //output.writeFully("handshake-success|$trueTime".toByteArray())
                output.writeFully("success|$trueTime\r\n".toByteArray())
                this@MainActivity.runOnUiThread(java.lang.Runnable {
                    var statusView = findViewById<TextView>(R.id.peerConnectionStatusView)
                    statusView.text = "Connected to peer : ${socket.remoteAddress}"
                    startRecording()
                })
            }
            else {
                //output.writeFully("handshake-failed".toByteArray())
                output.writeFully("failed|message format was wrong\r\n".toByteArray())

            }

        }
    }

    fun initTrueTime(ctx: Context) {
        if (isNetworkAvailable(ctx)) {
            if (!TrueTime.isInitialized()) {
                val trueTime = InitTrueTimeAsyncTask(ctx)
                trueTime.execute()
            }
        }
    }

    fun reverse(bytes: ByteArray): ByteArray {
        val buf = ByteArray(bytes.size)
        for (i in 0 until bytes.size) buf[i] = bytes.get(bytes.size - 1 - i)
        return buf
    }

    fun getLocalIPAddress(): String? {
        var result: String? = ""
        try {
            val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf: NetworkInterface = en.nextElement()
                val enumIpAddr: Enumeration<InetAddress> = intf.getInetAddresses()
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress: InetAddress = enumIpAddr.nextElement()
                    if (inetAddress.isSiteLocalAddress) {
                        var ipAddress =
                            BigInteger.valueOf(inetAddress.hashCode().toLong()).toByteArray();
                        var myaddr = InetAddress.getByAddress(reverse(ipAddress));
                        if (myaddr is Inet4Address) {
                            var hostaddr = myaddr.getHostAddress();
                            Log.i("MainActivity", "***** hostaddr=$hostaddr::${inetAddress}")
                            result = hostaddr
                        }
                    }
                }
            }
        } catch (ex: SocketException) {
            Log.e("MainActivity", ex.toString())
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d("MainActivity", "WIFI")
                    Log.d("MainActivity", "WIFI")
                    val wm = this.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val longIp = wm.connectionInfo.ipAddress.toLong()
                    val byteIp = BigInteger.valueOf(longIp).toByteArray().reversedArray()
                    val strIp = InetAddress.getByAddress(byteIp).hostAddress
                    Log.d("MainActivity", "ipaddress: $strIp")

                    ip = strIp
                    //populateUI(strIp)

                    true
                }
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d("MainActivity", "CELLULAR")

                    showDialog("Error", "Application needs wifi to work. Please connect to wifii and re-try!")

                    true
                }
                //for other device how are able to connect with Ethernet
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                //for check internet over Bluetooth
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        } else {
            val nwInfo = connectivityManager.activeNetworkInfo ?: return false
            return nwInfo.isConnected
        }
    }

    fun getTrueTime(): Date {
        if (TrueTime.isInitialized()) {
            return TrueTime.now()
        } else {
            return Date()
        }
    }
}