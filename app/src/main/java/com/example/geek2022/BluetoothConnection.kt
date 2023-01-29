package com.example.geek2022

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.util.*

/**
 * BLE端末をスキャンする時間をさだめる。今回は10秒。
  */
private const val SCAN_PERIOD: Long = 3000L

/**
 * BLE端末を検出してから接続するまでのインターバル．この間にデバイスの検索を止める．
  */
const val CONNECTION_INTERVAL = 500L

/**
 * GATT定義ディスクリプタの一つ．である，CCCD(Client Characteristic Configuration Descriptor). 端末にNotiyやIndicateを制御するにはこれが使われる．
 */
const val CCCD_UUID_STR = "293b9662-ec56-7167-8c3c-df51316e7ed8"

/**
 * 接続開始からの制限時間
 */
const val CONNECTION_PERIOD = 5000L

/**
 * GattCallbackにおいて，接続を制御する際に使用する定数．
 */
/* G */
const val STATE_DISCONNECTED = 0
const val STATE_CONNECTING = 1
const val STATE_CONNECTED = 2

/**
 * BLEデバイスとの通信を行うスレッドからメインスレッドへデータを渡す際に使用されるHandler．
 * 通信状態の変化に関する通知にも使われる．
 */

/**
 *  通信状態の変化を通知するHandlerで使用する定数
 */
const val DEVICE_SCANNING = 102
const val DEVICE_DISCONNECTED = 103
const val DEVICE_CONNECTING = 104
const val DEVICE_CONNECTED = 105


open class BluetoothConnectionHandler(
    private val handleOnConnectionStatusChanged: (status: Int)-> Unit
): Handler(Looper.getMainLooper()){
    override fun handleMessage(msg: Message) {
        when(msg.what){
            // デバイスが検索中のときに呼ばれる．objから対象のデバイス名を取り出してメソッドを呼ぶ．
            DEVICE_SCANNING ->{
                handleOnConnectionStatusChanged(DEVICE_SCANNING)
            }
            // デバイスが切断されたときに呼ばれる．objから対象のデバイス名を取り出してメソッドを呼ぶ．
            DEVICE_DISCONNECTED ->{
                handleOnConnectionStatusChanged(DEVICE_DISCONNECTED)
            }
            // デバイスが接続中のとき呼ばれる．objから対象のデバイス名を取り出してメソッドを呼ぶ．
            DEVICE_CONNECTING -> {
                handleOnConnectionStatusChanged(DEVICE_CONNECTING)
            }
            // デバイスが接続されたときに呼ばれる．objから対象のデバイス名を取り出してメソッドを呼ぶ．
            DEVICE_CONNECTED -> {
                handleOnConnectionStatusChanged(DEVICE_CONNECTED)
            }
        }
    }
}

/**
 * BLE接続に関するパーミッションのチェックを行う関数．
 */
private fun checkConnectionPermission(context: Context){
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e("debug", "BLUETOOTH_CONNECT permission is not granted")
        return
    }
}

/**
 * Bluetoothの接続が開始した際に始めに実行される処理．非同期的な処理を行うため，他スレッドとして実行される．
 */
class BleConnectionRunnable(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceName: String,
    private val handler: BluetoothConnectionHandler
): Runnable{
    var bleDeviceScanning: Boolean = false  /* デバイスがスキャン中かどうかを管理するBoolean変数 */
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private val leScanCallback = LeScanCallback(context, bluetoothLeScanner, this, handler)

    /**
     * このクラスがThreadに渡されてstartされたときに呼ばれる処理．ココから呼び出される処理はすべて他スレッドでの実行になる．
     * デバイスのスキャンを一致定時間行い，接続に成功すれば次は接続を試みる．
     */
    override fun run(){
        checkConnectionPermission(context)

        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build()
        val scanFilters = listOf<ScanFilter>(ScanFilter.Builder().setDeviceName(deviceName).build())
        when(bluetoothAdapter.isEnabled){
            true ->{
                // 事前に決めたスキャン時間を過ぎたらスキャンを停止する
                Handler(Looper.getMainLooper()).postDelayed({
                    // タイムアウト時間になってもまだスキャンをしていれば，スキャンを停止する．
                    if(bleDeviceScanning){

                        val connectionMessage = handler.obtainMessage(
                            DEVICE_DISCONNECTED,
                        )
                        connectionMessage.sendToTarget()

                        Toast.makeText(context, R.string.connection_failed, Toast.LENGTH_LONG)
                            .show()
                        bleDeviceScanning = false
                        Log.d("debug", "device not found: $deviceName")
                        bluetoothLeScanner.stopScan(leScanCallback)
                    }
                }, SCAN_PERIOD)

                val connectionMessage = handler.obtainMessage(
                    DEVICE_CONNECTING,
                )
                connectionMessage.sendToTarget()

                bleDeviceScanning = true
                Log.d("debug", "start scanning: $deviceName")
                bluetoothLeScanner.startScan(scanFilters, scanSettings, leScanCallback)
            }
            else -> {
                Log.d("debug", "bluetoothAdapter is not enabled")
                bleDeviceScanning = false
            }
        }
    }
    /**
     * デバイスの切断を人為的に行う際に呼ぶ．
     * この処理は他スレッドでは行われないので注意．
     */
    fun disconnect(){
        leScanCallback.disconnectGatt()
    }

    /**
     * 施錠．
     * ホントはもう少し別な場所に書きたいのだけど…
     */
    fun lock(){
        leScanCallback.lock()
    }

    /**
     * 解錠．
     */
    fun unlock(){
        leScanCallback.unLock()
    }
}

/**
 * 探していたBLEデバイスが見つかった，もしくはスキャンに失敗した時に呼ばれる処理．
 */
open class LeScanCallback(
    private val context: Context,
    private val bluetoothLeScanner: BluetoothLeScanner,
    private val bleConnectionRunnable: BleConnectionRunnable,
    private val handler: BluetoothConnectionHandler) : ScanCallback(){

    private var isAlreadyFound = false
    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * スキャンに失敗した際に呼ばれる処理．
     */
    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e("BLEDeviceScanFailed", "端末のスキャンに失敗しました")
    }

    /**
     * スキャンに成功した際に呼ばれる処理．
     * @param callbackType どのようにしてこのコールバックが呼ばれたのかを示す値．
     * @param result BLEデバイススキャンの結果．ここから見つかったデバイス名などのの情報がわかる．
     */
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        result?.let {
            checkConnectionPermission(context)
            // デバイスが見つかったので，デバイスの検索を止める．
            bleConnectionRunnable.bleDeviceScanning = false
            bluetoothLeScanner.stopScan(this)
            if(!isAlreadyFound) { // 各接続試行につき1回しか呼ばれないようにする．
                Log.d("debug", "device ${it.device.name} found")
                // デバイスが見つかってからデバイススキャンを停止するまでタイムラグがあるため，その時間を待ってから接続を開始する
                Handler(Looper.getMainLooper()).postDelayed({ bluetoothGatt = it.device.connectGatt(context, true, GattCallback(context, handler))}, CONNECTION_INTERVAL)
                isAlreadyFound = true
            }
        }
    }

    /**
     *     メインスレッドからデバイスを手動で切断するときに呼ばれる．
     */
    fun disconnectGatt(){
        checkConnectionPermission(context)
        if(bluetoothGatt == null){
            Log.e("debug", "bluetoothGatt to disconnect is null")
        }
        Log.d("debug", "disconnect from BLE device")
        bluetoothGatt?.close()
    }

    fun lock(){
        checkConnectionPermission(context)
        val service = bluetoothGatt?.getService(UUID.fromString("55725ac1-066c-48b5-8700-2d9fb3603c5e"))
        val characteristic = service?.getCharacteristic(UUID.fromString("69ddb59c-d601-4ea4-ba83-44f679a670ba"))
        characteristic?.let {
            Log.d("debug", "characteristic properties: ${it.properties}")
            it.setValue(byteArrayOf(0x00))
            bluetoothGatt?.writeCharacteristic(it)
        }
    }

    fun unLock(){
        checkConnectionPermission(context)
        val service = bluetoothGatt?.getService(UUID.fromString("55725ac1-066c-48b5-8700-2d9fb3603c5e"))
        val characteristic = service?.getCharacteristic(UUID.fromString("69ddb59c-d601-4ea4-ba83-44f679a670ba"))
        characteristic?.let {
            Log.d("debug", "characteristic properties: ${it.properties}")
            it.setValue(byteArrayOf(0x01))
            bluetoothGatt?.writeCharacteristic(it)
        }
    }
}

/**
 * デバイスの接続と切断を管理するコールバック関数が集まったクラス．
 */
class GattCallback(private val context: Context,
                   private val handler: BluetoothConnectionHandler) : BluetoothGattCallback() {
    private var connectionState = STATE_DISCONNECTED
    private var connectionTimedOut = false

    /**
     * BLEデバイスとの接続状況が変化すると呼ばれるメソッド．
     */
    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        checkConnectionPermission(context)
        Handler(Looper.getMainLooper()).postDelayed({ // 5秒間の間，接続を試行する．5秒経っても接続できない場合，タイムアウトしたことを示すメンバをtrueにして接続試行をやめる．
            connectionTimedOut = true
        }, CONNECTION_PERIOD)
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> { // 接続が確立したときに呼ばれる部分
                connectionState = STATE_CONNECTED

                val connectionMessage = handler.obtainMessage(
                    DEVICE_CONNECTED,
                )
                connectionMessage.sendToTarget()

                val isDiscoveringServices = gatt?.discoverServices()
                Log.d("debug", "Connected to GATT server. Attempting to start service discovery: $isDiscoveringServices")
            }
            BluetoothProfile.STATE_DISCONNECTED -> { // デバイスとの接続が切れた際に呼ばれる部分．
                connectionState = STATE_DISCONNECTED

                val connectionMessage = handler.obtainMessage(
                    DEVICE_CONNECTED,
                )
                connectionMessage.sendToTarget()

                Log.d("debug", "Disconnected from GATT server.")
                gatt?.close()
                when(status){
                    // コードが133(デバイスが見つからない)場合，接続をもう一度試行する動作を5秒間繰り返す．それ以外は，接続をやめる．
                    133 -> {
                        if (!connectionTimedOut){
                            Log.d("debug", "connection failed, retrying...")
                            gatt?.device?.connectGatt(context, true, this)
                        }else{
                            connectionTimedOut = false
                            Log.d("debug", "connection failed, connection timed out")
                        }
                    }
                }
            } else -> {
                gatt?.close()
            }
        }
    }

    /**
     * 接続後，BLEで使用される「サービス」が受け取ると呼ばれる．今回使うサービスは3つ目のサービスであるため，それを抽出する．
     */
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int){
        checkConnectionPermission(context)
        Log.d("debug", "${gatt.device.name}: service discovered")
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                Log.d("debug", "${gatt.device.name}: gatt success")
                val service = gatt.getService(UUID.fromString("55725ac1-066c-48b5-8700-2d9fb3603c5e"))
                val characteristic = service?.getCharacteristic(UUID.fromString("69ddb59c-d601-4ea4-ba83-44f679a670ba"))
                characteristic?.let {
//                    Log.d("debug", "characteristic properties: ${it.properties}")
                    val readResult = gatt.readCharacteristic(it)
                    Log.d("debug", "readCharacteristic: $readResult")
                    it.setValue(byteArrayOf(0x00))
                    gatt.writeCharacteristic(it)
                }
            }
            else -> {
                Log.d("debug", "onServicesDisconnected received: $status")
            }
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        checkConnectionPermission(context)
        super.onCharacteristicWrite(gatt, characteristic, status)
        gatt?.writeCharacteristic(characteristic)
    }

    /**
     * キャラクタリスティックを一番最初に受信するとココが呼ばれる．
     */
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ){
        Log.d("debug", "characteristic read")
        checkConnectionPermission(context)
        Log.d("debug", "characteristic status: $status")
        when(status){
            BluetoothGatt.GATT_SUCCESS -> {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(UUID.fromString(CCCD_UUID_STR))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    /**
     * デバイスから定期的にキャラクタリスティックとよばれる通信データが送られてくると呼ばれる関数．
     */
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        Log.d("debug", "characteristic changed")
        checkConnectionPermission(context)
        characteristic?.let { it ->
            val str = String(it.value)
            Log.d("debug", "characteristic value: $str")
        }
    }
}