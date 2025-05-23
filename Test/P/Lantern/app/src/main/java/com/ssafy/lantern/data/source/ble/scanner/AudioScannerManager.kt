package com.ssafy.lantern.data.source.ble.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

@SuppressLint("MissingPermission")
class AudioScannerManager(
    private val activity: Activity,
    private val onDeviceFound: (BluetoothDevice) -> Unit
) {
    companion object {
        private val SERVICE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
        private const val SCAN_PERIOD = 30000L
        private const val SCAN_INTERVAL = 5000L
    }

    private val bleScanner by lazy { // Renamed to avoid conflict with local variable if any, and 'ble' prefix for clarity
        (activity.getSystemService(Activity.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?.bluetoothLeScanner
    }
    private var scanCallback: ScanCallback? = null // Renamed to avoid conflict
    private val seenDevices = mutableSetOf<String>() // Renamed for clarity
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var periodicScanRunnable: Runnable? = null

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val currentScanner = this.bleScanner ?: return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (isScanning) {
            return
        }

        stopScanning()
        seenDevices.clear()

        try {
            scanCallback = object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) {
                    result.device?.let { device ->
                        val addr = device.address
                        if (seenDevices.add(addr)) {
                            onDeviceFound(device)
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    isScanning = false
                    handler.postDelayed({ startScanning() }, 2000)
                }
            }

            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
            )

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            currentScanner.startScan(filters, settings, scanCallback)
            isScanning = true

            handler.postDelayed({
                if (isScanning) {
                    stopScanning()
                    handler.postDelayed({ startScanning() }, 1000)
                }
            }, SCAN_PERIOD)
        } catch (e: Exception) {
            isScanning = false
            handler.postDelayed({ startScanning() }, 3000)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning || scanCallback == null) return
        val currentScanner = this.bleScanner ?: return

        try {
            currentScanner.stopScan(scanCallback)
        } catch (e: Exception) {
            // Error stopping scan
        } finally {
            isScanning = false
            scanCallback = null // Nullify callback after stopping
        }
    }

    fun checkScanningStatus() {
        if (!isScanning) {
            startScanning()
        }
    }

    fun enablePeriodicScanning() {
        checkScanningStatus() // Initial scan check
        if (periodicScanRunnable == null) {
            periodicScanRunnable = object : Runnable {
                override fun run() {
                    checkScanningStatus()
                    handler.postDelayed(this, SCAN_INTERVAL)
                }
            }
        }
        // Ensure only one instance of the runnable is scheduled
        handler.removeCallbacks(periodicScanRunnable!!)
        handler.postDelayed(periodicScanRunnable!!, SCAN_INTERVAL)
    }

    fun disablePeriodicScanning() {
        periodicScanRunnable?.let { handler.removeCallbacks(it) }
        periodicScanRunnable = null
        if (isScanning) { // Stop current scan if any
            stopScanning()
        }
    }

    fun getDiscoveredDevices(): Set<String> {
        return seenDevices.toSet()
    }
}