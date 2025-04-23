import 'dart:async';
import 'dart:io' show Platform; // Platform 확인용

import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const BleChatApp());
}

class BleChatApp extends StatelessWidget {
  const BleChatApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BLE Chat MVP',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true, // Material 3 사용 권장
      ),
      home: const ScanScreen(),
      debugShowCheckedModeBanner: false, // 디버그 배너 숨기기
    );
  }
}

class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key});

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  List<ScanResult> _scanResults = []; // 스캔 결과 저장 리스트
  bool _isScanning = false; // 현재 스캔 중인지 상태
  late StreamSubscription<List<ScanResult>>
      _scanResultsSubscription; // 스캔 결과 리스너
  late StreamSubscription<BluetoothAdapterState>
      _adapterStateSubscription; // 블루투스 상태 리스너
  BluetoothAdapterState _adapterState =
      BluetoothAdapterState.unknown; // 블루투스 어댑터 상태

  @override
  void initState() {
    super.initState();
    // 블루투스 상태 변화 감지 리스너 설정
    _adapterStateSubscription = FlutterBluePlus.adapterState.listen((state) {
      _adapterState = state;
      if (mounted) {
        // 위젯이 마운트된 상태인지 확인
        setState(() {}); // 화면 갱신하여 상태 표시
      }
    });

    // 스캔 결과 감지 리스너 설정
    _scanResultsSubscription = FlutterBluePlus.scanResults.listen((results) {
      // 기존 결과와 새로운 결과를 합치되, 중복 제거 (id 기준)
      // 필터링: 이름 없는 기기 제외 (선택 사항)
      final filteredResults =
          results.where((r) => r.device.platformName.isNotEmpty).toList();

      // 결과를 Set에 넣어 중복 제거 후 다시 List로 변환
      final uniqueResults = Map.fromEntries(filteredResults.map(
          (r) => MapEntry(r.device.remoteId.toString(), r))).values.toList();

      if (mounted) {
        // 위젯이 마운트된 상태인지 확인
        setState(() {
          _scanResults = uniqueResults; // 중복 제거된 결과로 업데이트
        });
      }
    }, onError: (e) {
      print("스캔 결과 리스닝 오류: $e"); // 오류 로그 출력
      _stopScan(); // 오류 발생 시 스캔 중지
    });
  }

  @override
  void dispose() {
    // 리스너 해제
    _adapterStateSubscription.cancel();
    _scanResultsSubscription.cancel();
    _stopScan(); // 화면 종료 시 스캔 중지 확실히 하기
    super.dispose();
  }

  // 권한 요청 및 확인 함수
  Future<bool> _requestPermissions() async {
    // --- Android 권한 요청 ---
    if (Platform.isAndroid) {
      // 위치 권한 요청 (BLE 스캔에 필요)
      var locationStatus = await Permission.locationWhenInUse.request();
      if (!locationStatus.isGranted) {
        print("위치 권한 거부됨");
        _showPermissionDialog("위치 권한", "BLE 스캔을 위해 위치 권한이 필요합니다.");
        return false;
      }

      // Android 12+ 블루투스 권한 요청
      // Build.VERSION.SDK_INT 같은 정보가 Flutter에 직접 없으므로, 일단 다 요청
      var scanStatus = await Permission.bluetoothScan.request();
      if (!scanStatus.isGranted) {
        print("블루투스 스캔 권한 거부됨");
        _showPermissionDialog("블루투스 스캔 권한", "주변 기기를 찾기 위해 블루투스 스캔 권한이 필요합니다.");
        return false;
      }

      var connectStatus = await Permission.bluetoothConnect.request();
      if (!connectStatus.isGranted) {
        print("블루투스 연결 권한 거부됨");
        _showPermissionDialog("블루투스 연결 권한", "기기와 연결하기 위해 블루투스 연결 권한이 필요합니다.");
        return false;
      }
      var advertiseStatus = await Permission.bluetoothAdvertise.request();
      if (!advertiseStatus.isGranted) {
        print("블루투스 광고 권한 거부됨");
        _showPermissionDialog(
            "블루투스 광고 권한", "다른 기기가 현재 기기를 찾도록 하기 위해 블루투스 광고 권한이 필요합니다.");
        // 광고 권한은 당장 스캔에는 필수 아니므로 false 리턴은 안함 (필요시 추가)
        // return false;
      }
    }
    // --- iOS 권한 요청 ---
    // iOS는 Info.plist 설정으로 충분하며, 사용 시 자동으로 프롬프트가 뜸.
    // 필요하다면 여기서 permission_handler로 상태 확인 가능
    else if (Platform.isIOS) {
      var bluetoothStatus = await Permission.bluetooth.request();
      if (!bluetoothStatus.isGranted) {
        print("iOS 블루투스 권한 거부됨");
        _showPermissionDialog("블루투스 권한", "주변 기기 탐색 및 연결을 위해 블루투스 권한이 필요합니다.");
        return false;
      }
      // iOS 위치 권한 확인 (선택적이지만 권장)
      var locationStatus = await Permission.locationWhenInUse.request();
      if (!locationStatus.isGranted) {
        print("iOS 위치 권한 거부됨");
        _showPermissionDialog("위치 권한", "주변 BLE 기기를 정확히 찾기 위해 위치 권한이 권장됩니다.");
        // 위치 권한 없어도 스캔은 가능할 수 있으므로 false 리턴은 안함 (정책에 따라 다름)
        // return false;
      }
    }

    print("모든 필수 권한 획득 완료");
    return true; // 모든 필수 권한 획득 성공
  }

  // 권한 필요 안내 다이얼로그
  void _showPermissionDialog(String title, String message) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: <Widget>[
            TextButton(
              child: const Text("설정 열기"),
              onPressed: () {
                openAppSettings(); // 앱 설정 화면으로 이동
                Navigator.of(context).pop();
              },
            ),
            TextButton(
              child: const Text("닫기"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
          ],
        );
      },
    );
  }

  // 스캔 시작 함수
  Future<void> _startScan() async {
    // 1. 권한 확인 및 요청
    bool permissionsGranted = await _requestPermissions();
    if (!permissionsGranted) {
      print("스캔 시작 실패: 필요한 권한 없음");
      return;
    }

    // 2. 블루투스 활성화 상태 확인
    if (_adapterState != BluetoothAdapterState.on) {
      print("스캔 시작 실패: 블루투스가 꺼져 있음");
      // 사용자에게 블루투스를 켜달라는 메시지 표시 (예: 스낵바)
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('BLE 스캔을 위해 블루투스를 활성화해주세요.')),
      );
      // 안드로이드에서 블루투스 활성화 요청 (선택 사항)
      // if (Platform.isAndroid) {
      //   await FlutterBluePlus.turnOn();
      // }
      return;
    }

    // 3. 스캔 시작
    try {
      if (mounted) {
        setState(() {
          _isScanning = true;
          _scanResults.clear(); // 새 스캔 시작 시 기존 결과 초기화
          print("스캔 시작...");
        });
      }

      // 이전에 연결된 기기 목록 가져오기 (옵션)
      // List<BluetoothDevice> connected = await FlutterBluePlus.connectedSystemDevices;
      // print("이미 연결된 기기: ${connected.length}");

      // 스캔 시작 (withServices: 특정 서비스 UUID 필터링 가능, timeout: 스캔 시간 제한)
      await FlutterBluePlus.startScan(
          // withServices: [Guid("원하는_서비스_UUID")], // 특정 서비스만 찾고 싶을 때
          timeout: const Duration(seconds: 15), // 15초 동안 스캔
          androidScanMode: AndroidScanMode.lowLatency // 스캔 모드 (안드로이드)
          );

      // 스캔 타임아웃 후 또는 stopScan 호출 시 스캔이 멈추면 여기로 올 수 있음
      // stopScan을 명시적으로 호출하는 로직 추가

      // 타임아웃 후 자동으로 스캔 중지 상태로 변경 (선택적: 수동 중지 버튼만 사용할 수도 있음)
      // Timer(Duration(seconds: 15), _stopScan);
    } catch (e) {
      print("스캔 시작 중 오류 발생: $e");
      _stopScan(); // 오류 발생 시 스캔 중지
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('스캔 시작 오류: ${e.toString()}')),
        );
      }
    } finally {
      // 스캔 타임아웃 또는 중지 후 상태 업데이트 (startScan이 끝나고 호출됨)
      if (mounted) {
        // setState(() { _isScanning = false; }); // stopScan에서 처리하도록 변경
        print("FlutterBluePlus.startScan() 완료됨");
      }
    }
  }

  // 스캔 중지 함수
  Future<void> _stopScan() async {
    try {
      await FlutterBluePlus.stopScan();
      print("스캔 중지됨");
    } catch (e) {
      print("스캔 중지 중 오류: $e");
    } finally {
      if (mounted) {
        setState(() {
          _isScanning = false;
        });
      }
    }
  }

  // 스캔 시작/중지 토글 함수
  void _toggleScan() {
    if (_isScanning) {
      _stopScan();
    } else {
      _startScan();
    }
  }

  // 기기 타일 생성 함수
  Widget _buildDeviceTile(ScanResult result) {
    return ListTile(
      title: Text(result.device.platformName.isNotEmpty
          ? result.device.platformName
          : 'Unknown Device'), // 기기 이름 (없으면 Unknown)
      subtitle:
          Text(result.device.remoteId.toString()), // 기기 ID (MAC 주소 또는 UUID)
      trailing: Text('${result.rssi} dBm'), // 신호 강도
      onTap: () {
        // TODO: 기기 연결 로직 구현
        print(
            '연결 시도: ${result.device.platformName} (${result.device.remoteId})');
        _stopScan(); // 연결 시도 전에 스캔 중지
        // Navigator.push(context, MaterialPageRoute(builder: (context) => DeviceScreen(device: result.device)));
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('BLE 주변 기기 스캔'),
        actions: [
          // 블루투스 상태 아이콘 표시
          Padding(
            padding: const EdgeInsets.only(right: 8.0),
            child: Icon(
              _adapterState == BluetoothAdapterState.on
                  ? Icons.bluetooth_connected
                  : Icons.bluetooth_disabled,
              color: _adapterState == BluetoothAdapterState.on
                  ? Colors.white
                  : Colors.grey,
            ),
          )
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text(
                _adapterState == BluetoothAdapterState.on
                    ? "블루투스 활성화됨"
                    : "블루투스 비활성화됨 - 스캔 불가",
                style: TextStyle(
                    color: _adapterState == BluetoothAdapterState.on
                        ? Colors.green
                        : Colors.red)),
          ),
          Expanded(
            // 스캔 결과가 없으면 안내 메시지 표시
            child: _scanResults.isEmpty
                ? Center(
                    child: Text(
                      _isScanning
                          ? '주변 기기를 찾는 중...'
                          : '스캔된 기기가 없습니다.\n아래 버튼을 눌러 스캔을 시작하세요.',
                      textAlign: TextAlign.center,
                    ),
                  )
                : ListView.builder(
                    itemCount: _scanResults.length,
                    itemBuilder: (context, index) {
                      return _buildDeviceTile(_scanResults[index]);
                    },
                  ),
          ),
        ],
      ),
      // 스캔 시작/중지 플로팅 액션 버튼
      floatingActionButton: FloatingActionButton(
        onPressed: (_adapterState == BluetoothAdapterState.on)
            ? _toggleScan
            : null, // 블루투스 켜져 있을 때만 활성화
        tooltip: _isScanning ? '스캔 중지' : '스캔 시작',
        backgroundColor: (_adapterState == BluetoothAdapterState.on)
            ? null
            : Colors.grey, // 비활성화 시 회색
        child: Icon(_isScanning ? Icons.stop : Icons.search),
      ),
    );
  }
}
