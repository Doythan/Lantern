package com.ssafy.lantern.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile // STATE_CONNECTED 등
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.lantern.data.source.ble.advertiser.AdvertiserManager
import com.ssafy.lantern.data.source.ble.gatt.GattClientManager
import com.ssafy.lantern.data.source.ble.gatt.GattServerManager
import com.ssafy.lantern.data.source.ble.scanner.ScannerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 상태 정의 개선
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(), // 메시지 타입 정의
    val scannedDevices: Map<String, String> = emptyMap(), // 스캔된 기기 목록 (Address to Name)
    val connectedDevice: BluetoothDevice? = null,
    val connectionState: Int = BluetoothProfile.STATE_DISCONNECTED,
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val requiredPermissionsGranted: Boolean = false,
    val errorMessage: String? = null
)

// 메시지 데이터 클래스
data class ChatMessage(
    val sender: String, // "나" 또는 상대방 이름/주소
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@SuppressLint("MissingPermission") // ViewModel 내부에서는 권한 확인되었다고 가정
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    // Hilt를 통해 주입 (DataModule에서 Singleton으로 제공)
    private val advertiserManager: AdvertiserManager,
    private val gattServerManager: GattServerManager,
    private val gattClientManager: GattClientManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Bluetooth Adapter
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothManager?.adapter
    }

    // ScannerManager는 ViewModel에서 직접 생성 및 관리
    private val scannerManager: ScannerManager by lazy {
        ScannerManager(application, ::handleScanResult)
    }

    init {
        Log.d("ChatViewModel", "Initializing...")
        checkInitialBluetoothState()
        // GattClient/Server 콜백 설정 (DataModule에서 임시 콜백으로 생성됨)
        setupGattCallbacks()
    }

    // GattClient/Server 콜백 설정
    private fun setupGattCallbacks() {
         // GattClientManager 콜백 재설정 (ViewModel 로직 연결)
        gattClientManager.onConnectionStateChange = ::handleClientConnectionStateChange
        gattClientManager.onMessageReceived = ::handleMessageReceived

        // GattServerManager 콜백 재설정 (ViewModel 로직 연결)
        gattServerManager.onConnectionStateChange = ::handleServerConnectionStateChange
        gattServerManager.onClientSubscribed = { device -> /* 클라이언트 구독 시 처리 */ }
        gattServerManager.onClientUnsubscribed = { device -> /* 클라이언트 구독 해제 시 처리 */ }
    }

    // 초기 블루투스 상태 확인
    private fun checkInitialBluetoothState() {
        _uiState.update { it.copy(isBluetoothEnabled = bluetoothAdapter?.isEnabled == true) }
    }

    // 권한 상태 업데이트
    fun updatePermissionStatus(granted: Boolean) {
        val wasGranted = _uiState.value.requiredPermissionsGranted
        _uiState.update { it.copy(requiredPermissionsGranted = granted) }
        if (granted && !wasGranted && _uiState.value.isBluetoothEnabled) {
            startBleOperations()
        } else if (!granted) {
            stopBleOperations()
            _uiState.update { it.copy(errorMessage = "BLE 기능을 사용하려면 권한이 필요합니다.") }
        }
    }

    // 블루투스 활성화 상태 업데이트
    fun updateBluetoothState(enabled: Boolean) {
        val wasEnabled = _uiState.value.isBluetoothEnabled
        _uiState.update { it.copy(isBluetoothEnabled = enabled) }
        if (enabled && !wasEnabled && _uiState.value.requiredPermissionsGranted) {
            startBleOperations()
        } else if (!enabled) {
            stopBleOperations()
            _uiState.update { it.copy(errorMessage = "블루투스를 활성화해주세요.") }
        }
    }

    // BLE 작업 시작
    fun startBleOperations() {
        if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
            Log.w("ChatViewModel", "Cannot start BLE operations. Permissions or Bluetooth disabled.")
            return
        }
        Log.d("ChatViewModel", "Starting BLE operations...")
        viewModelScope.launch {
            try {
                gattServerManager.openGattServer()
                advertiserManager.startAdvertising()
                scannerManager.startScanning(scanCallback) // ViewModel의 콜백 전달
                _uiState.update { it.copy(isScanning = true, isAdvertising = true, errorMessage = null) }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error starting BLE operations", e)
                _uiState.update { it.copy(errorMessage = "BLE 시작 오류: ${e.message}") }
            }
        }
    }

    // BLE 작업 중지
    fun stopBleOperations() {
        Log.d("ChatViewModel", "Stopping BLE operations...")
        viewModelScope.launch {
            try {
                scannerManager.stopScanning()
                advertiserManager.stopAdvertising()
                gattServerManager.closeGattServer()
                gattClientManager.disconnectAll()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        isAdvertising = false,
                        scannedDevices = emptyMap(),
                        connectedDevice = null,
                        connectionState = BluetoothProfile.STATE_DISCONNECTED
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error stopping BLE operations", e)
                // 오류 상태 업데이트 필요 시
            }
        }
    }

    // 메시지 전송
    fun sendMessage(message: String) {
        if (_uiState.value.connectionState != BluetoothProfile.STATE_CONNECTED) {
            _uiState.update { it.copy(errorMessage = "기기에 연결되지 않았습니다.") }
            return
        }
        Log.d("ChatViewModel", "Sending message: $message")
        viewModelScope.launch {
            gattServerManager.broadcastMessage(message)
            // 내가 보낸 메시지 UI 업데이트
            _uiState.update {
                it.copy(messages = it.messages + ChatMessage("나", message))
            }
        }
    }

    // 스캔 결과 처리 (ScannerManager 콜백)
    private fun handleScanResult(result: ScanResult) {
        if (!_uiState.value.requiredPermissionsGranted) return // 권한 없으면 무시
        val device = result.device
        val deviceName = device.name ?: "Unknown Device"
        val deviceAddress = device.address

        // 스캔된 기기 목록 업데이트 (중복 방지)
        if (!_uiState.value.scannedDevices.containsKey(deviceAddress)) {
            Log.d("ChatViewModel", "Device scanned: $deviceName ($deviceAddress)")
            _uiState.update { state ->
                state.copy(scannedDevices = state.scannedDevices + (deviceAddress to deviceName))
            }
        }

        // TODO: 자동 연결 로직? 여기서는 스캔 목록만 업데이트
        // 필요하다면 여기서 gattClientManager.connectToDevice(device) 호출
    }

    // 특정 기기에 연결 시도
    fun connectToDevice(deviceAddress: String) {
         if (!_uiState.value.requiredPermissionsGranted || !_uiState.value.isBluetoothEnabled) {
             _uiState.update { it.copy(errorMessage = "연결하려면 권한 및 블루투스 활성화가 필요합니다.") }
             return
         }
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device != null) {
             Log.i("ChatViewModel", "Attempting to connect to ${device.name ?: device.address}")
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            gattClientManager.connectToDevice(device)
        } else {
            Log.e("ChatViewModel", "Device not found: $deviceAddress")
             _uiState.update { it.copy(errorMessage = "기기를 찾을 수 없습니다.") }
        }
    }

    // Gatt Client 연결 상태 변경 처리
    private fun handleClientConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = newState, isConnecting = false) }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _uiState.update { it.copy(connectedDevice = device, messages = emptyList()) } // 연결 성공 시 메시지 초기화
                Log.i("ChatViewModel", "Client connected to: ${device.name ?: device.address}")
            } else {
                _uiState.update { it.copy(connectedDevice = null) }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                     Log.e("ChatViewModel", "Client connection failed with status: $status")
                    _uiState.update { it.copy(errorMessage = "연결 실패 (Status: $status)") }
                }
                Log.i("ChatViewModel", "Client disconnected from: ${device.name ?: device.address}")
            }
        }
    }

     // Gatt Server 연결 상태 변경 처리 (클라이언트가 서버에 연결했을 때)
    private fun handleServerConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
         viewModelScope.launch {
             if (newState == BluetoothProfile.STATE_CONNECTED) {
                  Log.i("ChatViewModel", "Server connection from: ${device.name ?: device.address}")
                 // 필요시 서버 연결 상태 UI 업데이트
             } else {
                 Log.i("ChatViewModel", "Server disconnection from: ${device.name ?: device.address}")
                 // 필요시 서버 연결 상태 UI 업데이트
             }
         }
    }

    // 메시지 수신 처리 (GattClientManager 콜백)
    private fun handleMessageReceived(device: BluetoothDevice, message: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "Message received from ${device.address}: $message")
            val senderName = _uiState.value.connectedDevice?.name ?: device.address
            _uiState.update {
                it.copy(messages = it.messages + ChatMessage(senderName, message))
            }
        }
    }

    // ScannerManager 내부 ScanCallback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ChatViewModel", "Scan failed with error code: $errorCode")
            _uiState.update { it.copy(isScanning = false, errorMessage = "기기 스캔 실패: $errorCode") }
        }
    }

    // ViewModel 소멸 시
    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "onCleared called. Stopping BLE operations.")
        stopBleOperations()
    }
} 