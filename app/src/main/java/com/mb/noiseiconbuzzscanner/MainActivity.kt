package com.mb.noiseiconbuzzscanner

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val targetService = UUID.fromString("0000FEE7-0000-1000-8000-00805F9B34FB")
    private val targetWrite = UUID.fromString("0000FEE8-0000-1000-8000-00805F9B34FB")
    private val targetNotify = UUID.fromString("0000FEE9-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
        }
        val heading = TextView(this).apply {
            text = "ICON BUZZ • BLE CHECK"
            textSize = 22f
            setTextColor(0xFF172554.toInt())
        }
        status = TextView(this).apply {
            text = "Read-only compatibility scanner"
            textSize = 16f
            setPadding(0, 18, 0, 12)
        }
        val scan = Button(this).apply {
            text = "1. Scan for watch"
            setOnClickListener { ensurePermissionsThenScan() }
        }
        val connect = Button(this).apply {
            text = "2. Connect to selected watch"
            setOnClickListener { connectSelected() }
        }
        val stop = Button(this).apply {
            text = "Stop scan / disconnect"
            setOnClickListener {
                stopScan()
                gatt?.close()
                gatt = null
                status.text = "Disconnected"
            }
        }
        log = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF222222.toInt())
            text = "Instructions:\n• Keep the watch near your phone.\n• If NoiseFit is connected, temporarily disconnect it in NoiseFit before testing.\n• This app does not send or modify anything on the watch.\n\n"
        }
        outer.addView(heading)
        outer.addView(status)
        outer.addView(scan)
        outer.addView(connect)
        outer.addView(stop)
        outer.addView(ScrollView(this).apply { addView(log) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissionsThenScan() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (hasPermissions()) startScan()
        else requestPermissions(needed, 77)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            status.text = "Bluetooth permission was not granted."
        }
    }

    private fun startScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            status.text = "Turn on Bluetooth, then scan again."
            return
        }
        found.clear()
        log.text = "Scanning for nearby BLE devices for 12 seconds…\n"
        scanner = adapter.bluetoothLeScanner
        try {
            scanner?.startScan(scanCallback)
            status.text = "Scanning… keep the watch awake and nearby."
            handler.postDelayed({ stopScan(); status.text = "Scan finished. Select a device below." }, 12_000)
        } catch (e: SecurityException) {
            status.text = "Bluetooth permission error: ${e.message}"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val key = device.address ?: return
            if (!found.containsKey(key)) {
                found[key] = device
                val name = try { device.name ?: result.scanRecord?.deviceName ?: "Unnamed BLE device" }
                    catch (_: SecurityException) { "BLE device" }
                runOnUiThread {
                    log.append("FOUND: $name\nAddress: $key\nRSSI: ${result.rssi} dBm\n\n")
                    log.append("To select, use the device address in the next prompt if multiple devices appear.\n")
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { status.text = "Scan failed: code $errorCode" }
        }
    }

    private fun stopScan() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    private fun connectSelected() {
        if (found.isEmpty()) {
            Toast.makeText(this, "Scan first. No devices found yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val devices = found.entries.toList()
        val labels = devices.mapIndexed { i, entry ->
            val name = try { entry.value.name ?: "BLE device" } catch (_: Exception) { "BLE device" }
            "$i • $name • ${entry.key}"
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Choose your watch")
            .setItems(labels) { _, which -> connect(devices[which].value) }
            .show()
    }

    private fun connect(device: BluetoothDevice) {
        stopScan()
        status.text = "Connecting…"
        try {
            gatt?.close()
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            status.text = "Connect permission error: ${e.message}"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    status.text = "Connected. Reading services…"
                    log.append("\nCONNECTED. Discovering services…\n")
                    try { g.discoverServices() } catch (e: SecurityException) {
                        log.append("Permission error: ${e.message}\n")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    status.text = "Disconnected (status $statusCode)"
                    log.append("\nDISCONNECTED. GATT status: $statusCode\n")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            runOnUiThread {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    status.text = "Service discovery failed: $statusCode"
                    return@runOnUiThread
                }
                val services = g.services
                log.append("\n--- GATT SERVICES (${services.size}) ---\n")
                services.forEach { service ->
                    log.append("SERVICE ${service.uuid}\n")
                    service.characteristics.forEach { ch ->
                        val p = ch.properties
                        val flags = buildList {
                            if (p and 0x02 != 0) add("READ")
                            if (p and 0x04 != 0) add("WRITE_NO_RESPONSE")
                            if (p and 0x08 != 0) add("WRITE")
                            if (p and 0x10 != 0) add("NOTIFY")
                            if (p and 0x20 != 0) add("INDICATE")
                        }.joinToString(", ")
                        log.append("  CHAR ${ch.uuid} [$flags]\n")
                    }
                }
                val service = services.firstOrNull { it.uuid == targetService }
                val write = service?.getCharacteristic(targetWrite)
                val notify = service?.getCharacteristic(targetNotify)
                val matched = service != null && write != null && notify != null
                status.text = if (matched) "POSSIBLE MATCH: FEE7/FEE8/FEE9 found" else "Generic BLE connection OK; sender UUIDs not confirmed"
                log.append("\n--- COMPATIBILITY CHECK ---\n")
                log.append("Expected service FEE7: ${service != null}\n")
                log.append("Expected write characteristic FEE8: ${write != null}\n")
                log.append("Expected notify characteristic FEE9: ${notify != null}\n")
                log.append("\nThis is only a protocol fingerprint. No data was written to the watch.\n")
            }
        }
    }

    override fun onDestroy() {
        stopScan()
        gatt?.close()
        gatt = null
        super.onDestroy()
    }
}
