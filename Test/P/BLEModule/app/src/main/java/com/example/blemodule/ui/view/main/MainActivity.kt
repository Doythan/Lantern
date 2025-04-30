package com.example.blemodule.ui.view.main

/* ---------- Android ---------- */
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager

/* ---------- App ---------- */
import com.example.blemodule.R
import com.example.blemodule.data.state.AdvertisingState
import com.example.blemodule.data.state.ScanState
import com.example.blemodule.databinding.ActivityMainBinding
import com.example.blemodule.service.BleService
import com.example.blemodule.ui.viewmodel.main.MainViewModel
import com.example.blemodule.ui.viewmodel.main.MainViewModelFactory
import com.example.blemodule.util.BluetoothUtils
import com.example.blemodule.util.PermissionHelper
import com.example.blemodule.util.Constants
import com.example.blemodule.data.model.BleDevice

/* ---------- Kotlin ---------- */
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /* ---------- 필드 ---------- */
    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    private var bleService: BleService? = null
    private var isServiceBound = false

    private lateinit var viewModel: MainViewModel
    private var viewModelFactory: MainViewModelFactory? = null

    /* 주변 기기 목록 */
    private lateinit var deviceAdapter: DeviceAdapter
    private var selectedTargetId: String? = null

    /* 런처 */
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestEnableBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    /* ---------- ServiceConnection ---------- */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            log("onServiceConnected")
            bleService = (service as BleService.LocalBinder).getService()
            isServiceBound = true

            viewModelFactory =
                MainViewModelFactory(bleService!!.getRepositoryInstance())
            viewModel = ViewModelProvider(
                this@MainActivity,
                viewModelFactory!!
            )[MainViewModel::class.java]

            setupObservers()
            updateUiByServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isServiceBound = false
            updateUiByServiceState()
        }
    }

    /* ---------- 생명주기 ---------- */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initLaunchers()
        initRecycler()
        initButtons()
        updateUiInitial()

        bindBleService()
    }

    override fun onDestroy() {
        if (isServiceBound) unbindService(serviceConnection)
        super.onDestroy()
    }

    /* ---------- 초기화 ---------- */
    private fun initLaunchers() {
        requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (PermissionHelper.hasBlePermissions(this)) startServiceFlow()
                else toast("필수 BLE 권한이 거부되었습니다")
            }

        requestEnableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (BluetoothUtils.isBluetoothEnabled(this)) startServiceFlow()
                else toast("블루투스를 켜야 합니다")
            }

        requestNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }
    }

    private fun initRecycler() {
        deviceAdapter = DeviceAdapter { device ->
            viewModel.connectTo(device.address)
            selectedTargetId = device.name ?: device.address
            toast("연결 요청 → ${selectedTargetId}")
        }
        binding.rvDevices.apply {
            adapter = deviceAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }
        binding.tvLog.movementMethod = ScrollingMovementMethod()
    }

    private fun initButtons() = with(binding) {
        btnServiceToggle.setOnClickListener {
            if (!::viewModel.isInitialized) {
                toast("서비스 준비 중입니다...")
                return@setOnClickListener
            }
            if (viewModel.isServiceActive.value == true) stopBleService()
            else startServiceFlow()
        }

        btnSendChat.setOnClickListener {
            val txt = etChat.text.toString()
            viewModel.sendChat(selectedTargetId, txt)
            etChat.text.clear()
        }
    }

    /* ---------- 시작 플로우 ---------- */
    private fun startServiceFlow() {
        val idInput = binding.etMyDeviceId.text.toString()
        if (!::viewModel.isInitialized) return

        if (!viewModel.validateAndSetId(idInput, ::startBleService, ::stopBleService)) {
            toast("닉네임은 1~${Constants.MAX_NICKNAME_LENGTH}자, 중복 불가")
            return
        }

        if (!BluetoothUtils.isBluetoothEnabled(this)) {
            requestEnableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (!PermissionHelper.hasBlePermissions(this)) {
            PermissionHelper.requestBlePermissions(requestPermissionsLauncher)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)
        ) {
            PermissionHelper.requestNotificationPermission(requestNotificationPermissionLauncher)
        }

        startBleService(binding.etMyDeviceId.text.toString())
    }

    /* ---------- Service 제어 ---------- */
    private fun startBleService(deviceId: String) {
        if (isServiceBound && bleService != null) {
            bleService!!.getRepositoryInstance().setMyDeviceId(deviceId)
            if (!bleService!!.isRunning()) bleService!!.startBleOperationsFromClient()
            return
        }
        val intent = BleService.newStartIntent(this, deviceId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        bindBleService()
    }

    private fun stopBleService() {
        bleService?.stopBleService()
    }

    private fun bindBleService() {
        bindService(Intent(this, BleService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /* ---------- LiveData observe ---------- */
    private fun setupObservers() {
        viewModel.isServiceActive.observe(this) { updateUiByServiceState() }

        viewModel.scannedDevices.observe(this) { list -> deviceAdapter.submitList(list) }

        viewModel.connectedDevices.observe(this) { list ->
            log("연결: ${list.map(BleDevice::getDeviceNameSafe)}")
        }

        viewModel.logMessages.observe(this) { logs ->
            binding.tvLog.text = logs.joinToString("\n")
            binding.svLogContainer.post {
                binding.svLogContainer.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }

        viewModel.receivedMessage.observe(this) { msg ->
            toast("${msg.sourceId}: ${msg.payload}")
        }

        viewModel.scanState.observe(this) {
            if (it is ScanState.Failed) toast("스캔 실패: ${it.message}")
        }
        viewModel.advertisingState.observe(this) {
            if (it is AdvertisingState.Failed) toast("광고 실패: ${it.message}")
        }
    }

    /* ---------- UI ---------- */
    private fun updateUiInitial() = with(binding) {
        btnServiceToggle.text = getString(R.string.start_service)
        etMyDeviceId.isEnabled = true
        etChat.isEnabled = false
        btnSendChat.isEnabled = false
    }

    private fun updateUiByServiceState() = with(binding) {
        val active = viewModel.isServiceActive.value == true
        btnServiceToggle.text =
            if (active) getString(R.string.stop_service) else getString(R.string.start_service)
        etMyDeviceId.isEnabled = !active
        etChat.isEnabled = active
        btnSendChat.isEnabled = active
    }

    /* ---------- Util ---------- */
    private fun log(msg: String) = Log.d(TAG, msg)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
