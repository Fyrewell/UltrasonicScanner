package br.unisc.sisemb.ultrasonicscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    val deviceControl = DeviceControl()
    var mScannerView: ScannerView? = null

    companion object {
        val RESULT_BLE_OK = 1
    }

    internal class Package(
        val iot: Int,
        val sk: IntArray,
        val pl: Int,
        val payload: IntArray,
        val crc: Int,
        val eot: Int) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val fab = findViewById(R.id.bluetooth) as FloatingActionButton
        fab.setOnClickListener { view ->
            val deviceScanIntent = Intent(this, DeviceScanActivity::class.java)
            startActivityForResult(deviceScanIntent, RESULT_BLE_OK)
        }

        mScannerView = findViewById(R.id.scannerView) as ScannerView

        registerReceiver(broadcastReceiver, IntentFilter("PACKAGE_RECEIVED"))
    }

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MAIN", intent.getByteArrayExtra("package").toString())

            val pack: Package = intArrayToPackage(byteArrayToIntArray(intent.getByteArrayExtra("package")))
            Log.d("IOT", pack.iot.toString())
            Log.d("PAYLOAD1", pack.payload[1].toString())
            Log.d("PAYLOAD2", pack.payload[2].toString())

            mScannerView?.angle = retornaAngulo(pack)
            mScannerView?.distance = retornaDistancia(pack)
            mScannerView?.invalidate()
        }
    }

    fun byteArrayToIntArray(pack: ByteArray): IntArray {
        val packIntArray: IntArray = IntArray(pack.size)
        for ((i, byteChar) in pack.withIndex()) {
            if (byteChar < 0){
                val unsignedVal = 255 + byteChar.toInt()
                packIntArray.set(i, unsignedVal)
            }else{
                packIntArray.set(i, byteChar.toInt())
            }
        }
        return packIntArray
    }

    internal fun retornaAngulo(p: Package): Float {
        return (p.payload[1] + p.payload[2] + 135).toFloat()
    }

    internal fun retornaDistancia(p: Package): Float {
        return (p.payload[3] + p.payload[4]).toFloat()
    }

    private fun intArrayToPackage(intPack: IntArray): Package {
        return Package(
            intPack[0],
            intPack.copyOfRange(1, 8),
            intPack[9],
            intPack.copyOfRange(10, 10 + intPack[9]),
            intPack[10 + intPack[9]],
            intPack[11 + intPack[9]]
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.action_settings) {
            // test update
            mScannerView?.angle = 200f
            mScannerView?.invalidate()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_BLE_OK) {
            Log.d("DEVICE_NAME", data?.getStringExtra("DEVICE_NAME"))
            Log.d("DEVICE_ADDRESS", data?.getStringExtra("DEVICE_ADDRESS"))

            deviceControl.mDeviceAddress = data?.getStringExtra("DEVICE_ADDRESS")
            val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
            bindService(gattServiceIntent, deviceControl.mServiceConnection, Context.BIND_AUTO_CREATE)

            registerReceiver(deviceControl.mGattUpdateReceiver, deviceControl.makeGattUpdateIntentFilter())
            if (deviceControl.mBluetoothLeService != null) {
                val result = deviceControl.mBluetoothLeService!!.connect(deviceControl.mDeviceAddress)
                Log.d("rds", "Connect request result=" + result)
            }
        }

    }

    override fun onPause() {
        super.onPause()
        if (deviceControl.mDeviceAddress != null) {
            unregisterReceiver(deviceControl.mGattUpdateReceiver)
            deviceControl.mDeviceAddress = null
            unbindService(deviceControl.mServiceConnection)
            deviceControl.mBluetoothLeService = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(deviceControl.mServiceConnection)
        deviceControl.mBluetoothLeService = null
        unregisterReceiver(broadcastReceiver)
    }

}
