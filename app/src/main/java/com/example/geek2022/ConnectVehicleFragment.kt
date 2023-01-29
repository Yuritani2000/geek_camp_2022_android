package com.example.geek2022

import android.Manifest
import android.app.Instrumentation
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import org.w3c.dom.Text
import java.security.Permission

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

private var isPermissionsGranted = false

private var bleConnectionRunnable: BleConnectionRunnable? = null

private var tvConnectionStatus: TextView? = null

private var tvVehicleName: TextView? = null

/**
 * A simple [Fragment] subclass.
 * Use the [ConnectVehicleFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConnectVehicleFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        // 権限周りをリクエストする
        isPermissionsGranted = requestPermissions()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connect_vehicle, container, false)
    }

    private fun requestPermissions(): Boolean {
        var result = false

        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )

        val deniedPermissions = mutableListOf<String>()

        if(Build.VERSION.SDK_INT > 30){
            for(permission in permissions){
                if(ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED){
                    deniedPermissions.add(permission)
                }
            }
        }

        if(deniedPermissions.size == 0){
            return true
        }

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results: Map<String, Boolean> ->
                results.forEach { (p , isGranted) ->
                    if(!isGranted){
                        result = false
                    }
                }
            }
        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())

        return result
//        var result1 = false
//        var result2 = false
//        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
//            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ it ->
//                result1 = it
//            }
//            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
//        }
//
//        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED){
//            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()){ it ->
//                result2 = it
//            }
//            launcher.launch(Manifest.permission.BLUETOOTH_SCAN)
//        }
//
//
//        return result1 && result2
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?){
        super.onViewCreated(view, savedInstanceState)
        tvConnectionStatus = view.findViewById<TextView>(R.id.tv_connection_status)
        val btConnect = view.findViewById<Button>(R.id.bt_connect)
        val btDisconnect = view.findViewById<Button>(R.id.bt_disconnect)
        val btRetrySearch = view.findViewById<Button>(R.id.bt_retry_search)
        val btLock = view.findViewById<Button>(R.id.bt_lock)
        val btUnLock = view.findViewById<Button>(R.id.bt_unlock)
        tvVehicleName = view.findViewById<TextView>(R.id.tv_vehicle_name)

        btConnect.setOnClickListener {
            tvConnectionStatus?.setText(R.string.search_vehicle)
            Log.d("debug", "connection start")
            bluetoothAdapter?.let { connect(it) }
        }

        btDisconnect.setOnClickListener {
            disconnect()
            tvConnectionStatus?.setText(R.string.disconnect)
            tvVehicleName?.text = ""
        }

        btRetrySearch.setOnClickListener {
            tvConnectionStatus?.setText(R.string.search_vehicle)
            bluetoothAdapter?.let { connect(it) }
        }

        btLock.setOnClickListener {
            bleConnectionRunnable?.lock()
        }

        btUnLock.setOnClickListener {
            bleConnectionRunnable?.unlock()
        }

        setupBle()
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun setupBle(){
        if(isPermissionsGranted) {
            bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
                val startForResult =
                    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
                startForResult.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    private fun connect(bluetoothAdapter: BluetoothAdapter){
        /**
         * デバイスの接続状態が変化したときに呼ばれるメソッド
         * */
        val handleOnConnectionStatusChanged = fun(status: Int){
            // 表示するテキストとその色が入る変数
            var text = ""
            var textColor = 0
            // 接続状態が「切断」か，「接続中」か，「接続済み」かを見分け，入れるテキストと色を指定
            when(status){
                DEVICE_SCANNING -> {
                    tvConnectionStatus?.text = getString(R.string.search_vehicle)
                }
                DEVICE_DISCONNECTED -> {
                    tvConnectionStatus?.text = getString(R.string.disconnect)
                    tvVehicleName?.text = ""
                }
                DEVICE_CONNECTING -> {
                    tvConnectionStatus?.text = getString(R.string.connecting_vehicle)
                }
                DEVICE_CONNECTED -> {
                    tvConnectionStatus?.text = getString(R.string.connected)
                    tvVehicleName?.text = "Toyota_Ractis"
                }
            }
        }

        val handler = BluetoothConnectionHandler(handleOnConnectionStatusChanged)
        bleConnectionRunnable = BleConnectionRunnable(requireActivity(), bluetoothAdapter, "MyBLEDevice", handler )
        val bleThread = Thread(bleConnectionRunnable)
        bleThread.start()
    }

    private fun disconnect(){
        bleConnectionRunnable?.disconnect()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ConnectVehicleFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ConnectVehicleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}