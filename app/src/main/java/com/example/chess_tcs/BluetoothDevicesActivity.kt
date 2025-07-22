package com.example.chess_tcs

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothDevicesActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var listView: ListView
    private val deviceList = ArrayList<BluetoothDevice>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_devices)

        listView = findViewById(R.id.devices_list)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Urządzenie nie obsługuje Bluetooth", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        } else {
            checkPermissionsAndShowDevices()
        }
    }

    private fun checkPermissionsAndShowDevices() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // nowy Android (+10?)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            // stary Android
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        } else {
            showPairedDevices()
        }
    }

    private fun showPairedDevices() {
        try {
            val pairedDevices = bluetoothAdapter.bondedDevices
            val deviceNames = ArrayList<String>()
            deviceList.clear()

            if (pairedDevices.isEmpty()) {
                Toast.makeText(this, "Brak sparowanych urządzeń", Toast.LENGTH_SHORT).show()
            } else {
                for (device in pairedDevices) {
                    val name = device.name ?: "Nieznane urządzenie"
                    val address = device.address
                    deviceNames.add("$name\n$address")
                    deviceList.add(device)
                }
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
            listView.adapter = adapter

            listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedDevice = deviceList[position]
                val intent = Intent(this, LocalGameActivity::class.java).apply {
                    putExtra("GAME_MODE", "BLUETOOTH")
                    putExtra("DEVICE_ADDRESS", selectedDevice.address)
                }
                startActivity(intent)
            }

        } catch (e: SecurityException) {
            Toast.makeText(this, "Brak uprawnień do Bluetooth", Toast.LENGTH_SHORT).show()
            Log.e("Bluetooth", "SecurityException", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showPairedDevices()
            } else {
                Toast.makeText(this, "Aby kontynuować, musisz przyznać wymagane uprawnienia Bluetooth", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            checkPermissionsAndShowDevices()
        } else if (requestCode == 1) {
            Toast.makeText(this, "Bluetooth musi być włączony", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    fun createGame(view: View) {
        val intent = Intent(this, LocalGameActivity::class.java)
        intent.putExtra("GAME_MODE", "BLUETOOTH")
        startActivity(intent)
    }
}
