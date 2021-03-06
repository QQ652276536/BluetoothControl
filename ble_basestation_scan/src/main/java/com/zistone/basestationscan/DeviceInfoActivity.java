package com.zistone.basestationscan;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zistone.libble.util.BleEvent;
import com.zistone.libble.util.MyBleConnectListener;
import com.zistone.libble.util.MyBleMessageListener;
import com.zistone.libble.util.MyBleUtil;
import com.zistone.libble.util.MyConvertUtil;
import com.zistone.libble.util.MyFileUtil;
import com.zistone.libble.util.MyProgressDialogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class DeviceInfoActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "DeviceInfoActivity";
    private static int SPRING_GREEN = Color.parseColor("#3CB371");
    //版本信息及内部时间
    private static final String CMD_20 = "68000000000000682000E316";
    //电池电量
    private static final String CMD_21 = "68000000000000682100E316";
    //设备状态、连接的标签数、信号强度、基站类型
    private static final String CMD_22 = "68000000000000682200E316";
    //设置/查询相对位置
    private static final String CMD_23 = "68000000000000682300E316";
    //内部日志（开始查询）
    private static final String CMD_2A_START = "68000000000000682A0100E316";
    //内部日志（继续查询）
    private static final String CMD_2A_CONTINUE = "68000000000000682A0101E316";
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static int TASK_TIME = 1 * 1000;

    private ImageButton _btnReturn, _btnTop, _btnBottom, _btnClear;
    private TextView _txtLocationUnit, _txtRssi, _txtType, _txtX2, _txtY2, _txtZ2, _txtName, _txtAddress, _txtVersion, _txtTime, _txtState,
            _txtLabelNum, _txtDebug, _txtElec, _txtVol, _txtElecState;
    private BluetoothDevice _bluetoothDevice;
    private Map<String, UUID> _uuidMap;
    private Toolbar _toolbar;
    private Timer _timer;
    private TimerTask _timerTask;
    private MyHandler _myHandler;
    private boolean _taskIsRuning = false, _isConnected = false;
    private LinearLayout _llDebug;
    private Button _btnDebug;
    private MyBleConnectListener _connectListener;
    private MyBleMessageListener _messageListener;
    private int _currentLogtCount = 0, _totalLogCount = 0, _logStartOrContinue = 0;
    //内部日志线程
    private boolean _searchLogThreadFlag = false;
    private Thread _searchLogThread;
    private FileOutputStream _logFileOutputStream;
    private OutputStreamWriter _logOutputStreamWriter;

    static class MyHandler extends Handler {
        WeakReference<DeviceInfoActivity> _weakReference;
        DeviceInfoActivity _deviceInfoActivity;

        public MyHandler(DeviceInfoActivity activity) {
            _weakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message message) {
            if (_weakReference.get() == null)
                return;
            try {
                _deviceInfoActivity = _weakReference.get();
                if (message.what == -99) {
                    _deviceInfoActivity._txtDebug.append(message.obj + "\n");
                } else {
                    String[] strArray = (String[]) message.obj;
                    String cmdStr = Arrays.toString(strArray).replaceAll("[\\s|\\[|\\]|,]", "");
                    switch (message.what) {
                        //版本信息及内部时间
                        case 20: {
                            String versionStr = MyConvertUtil.HexStrToStr(strArray[10] + strArray[11] + strArray[12] + strArray[13]);
                            versionStr = MyConvertUtil.StrAddCharacter(versionStr, 1, ".").replaceFirst("\\.", "");
                            _deviceInfoActivity._txtVersion.setText(versionStr);
                            String yearStr = "20" + strArray[14];
                            int month = Integer.parseInt(strArray[15]);
                            int day = Integer.parseInt(strArray[16]);
                            int hour = Integer.parseInt(strArray[17]);
                            String hourStr = MyConvertUtil.AddZeroForNum(hour + "", 2, true);
                            int minute = Integer.parseInt(strArray[2]);
                            String minuteStr = MyConvertUtil.AddZeroForNum(minute + "", 2, true);
                            int second = Integer.parseInt(strArray[3]);
                            String secondStr = MyConvertUtil.AddZeroForNum(second + "", 2, true);
                            String timeStr = yearStr + "-" + month + "-" + day + " " + hourStr + ":" + minuteStr + ":" + secondStr;
                            _deviceInfoActivity._txtTime.setText(timeStr);
                            cmdStr = MyConvertUtil.StrAddCharacter(cmdStr, 2, " ");
                            String logStr = "版本信息和内部时间\n" + cmdStr + "\n版本：" + versionStr + "，内部时间：" + timeStr + "\n\n";
                            Log.i(TAG, logStr);
                            _deviceInfoActivity._txtDebug.append(logStr);
                        }
                        break;
                        //电池电量
                        case 21: {
                            int elec = Integer.parseInt(strArray[10], 16);
                            _deviceInfoActivity._txtElec.setText(elec + "%");
                            double voltage = (double) Integer.parseInt(strArray[11] + strArray[12], 16) / 1000;
                            _deviceInfoActivity._txtVol.setText(voltage + "V");
                            //0没有外电、1正在充电、2充电完毕
                            int state = Integer.parseInt(strArray[13], 16);
                            String stateStr = "";
                            if (state == 0)
                                stateStr = "没有外电";
                            else if (state == 1)
                                stateStr = "正在充电";
                            else if (state == 2)
                                stateStr = "充电完毕";
                            _deviceInfoActivity._txtElecState.setText(stateStr);
                            double temp = (Integer.valueOf(strArray[14] + strArray[15], 16) & 0xFFFF) / 10.0;
                            String logStr = "电池电量\n" + cmdStr + "电量：" + elec + "%，电压：" + voltage + "mV，充电状态：" + stateStr + "，温度：" + temp + "\n";
                            Log.i(TAG, logStr);
                            _deviceInfoActivity._txtDebug.append(logStr);
                        }
                        break;
                        //状态、连接的标签数、信号强度、基站类型
                        case 22: {
                            int state = Integer.valueOf(strArray[10], 16);
                            String stateStr = "已断开";
                            switch (state) {
                                case 0:
                                    stateStr = "已断开";
                                    break;
                                case 1:
                                    stateStr = "初始化";
                                    break;
                                case 2:
                                    stateStr = "正在连接AP";
                                    break;
                                case 3:
                                    stateStr = "已连接AP";
                                    break;
                                case 4:
                                    stateStr = "正在连接IP";
                                    break;
                                case 5:
                                    stateStr = "已连接IP";
                                    break;
                                case 6:
                                    stateStr = "设备正在授权";
                                    break;
                                case 7:
                                    stateStr = "设备鉴权成功";
                                    break;
                            }
                            _deviceInfoActivity._txtState.setText(stateStr + "");
                            int count = Integer.valueOf(strArray[11] + strArray[12], 16);
                            _deviceInfoActivity._txtLabelNum.setText(count + "");
                            int rssi = Integer.valueOf(strArray[13], 16).shortValue();
                            if (rssi > 0 && rssi < 256) {
                                rssi = rssi - 256;
                                _deviceInfoActivity._txtRssi.setText(rssi + "dBm");
                            }
                            int type = Integer.parseInt(strArray[14]);
                            String typeStr = "定位基站";
                            switch (type) {
                                case 0:
                                    typeStr = "定位基站";
                                    break;
                                case 1:
                                    typeStr = "测量基站";
                                    break;
                                case 2:
                                    typeStr = "其它";
                                    break;
                            }
                            _deviceInfoActivity._txtType.setText(typeStr);
                            cmdStr = MyConvertUtil.StrAddCharacter(cmdStr, 2, " ");
                            String logStr =
                                    "状态、连接的标签数、信号强度、基站类别\n" + cmdStr + "\n状态：" + state + "，连接的标签数：" + count + "，信号强度：" + rssi + "基站类别：" + type +
                                            "\n\n";
                            Log.i(TAG, logStr);
                            _deviceInfoActivity._txtDebug.append(logStr);
                        }
                        break;
                        //相对位置
                        case 23: {
                            int type = -1;
                            if (!"FF".equals(strArray[10].toUpperCase())) {
                                type = Integer.parseInt(strArray[10]);
                                switch (type) {
                                    case 1:
                                        _deviceInfoActivity._txtLocationUnit.setText("（厘米）");
                                        break;
                                    case 2:
                                        _deviceInfoActivity._txtLocationUnit.setText("（分米）");
                                        break;
                                    case 3:
                                        _deviceInfoActivity._txtLocationUnit.setText("（米）");
                                        break;
                                }
                            }
                            String xStr = strArray[11] + strArray[12];
                            int x = Integer.parseInt(xStr, 16);
                            String yStr = strArray[13] + strArray[14];
                            int y = Integer.parseInt(yStr, 16);
                            String zStr = strArray[15] + strArray[16];
                            int z = Integer.parseInt(zStr, 16);
                            _deviceInfoActivity._txtX2.setText(x + "");
                            _deviceInfoActivity._txtY2.setText(y + "");
                            _deviceInfoActivity._txtZ2.setText(z + "");
                            cmdStr = MyConvertUtil.StrAddCharacter(cmdStr, 2, " ");
                            String logStr = "相对位置\n" + cmdStr + "\n坐标单位：" + type + "，X：" + xStr + "，Y：" + yStr + "，Z" + "：" + zStr + "\n\n";
                            Log.i(TAG, logStr);
                            _deviceInfoActivity._txtDebug.append(logStr);
                        }
                        break;
                        //内部日志
                        case 42: {
                            try {
                                int len = Integer.parseInt(strArray[9], 16);
                                String logStr = "内部日志\n" + cmdStr;
                                int result = Integer.parseInt(strArray[10], 16);
                                //没有后续记录，已读完
                                if (result == 0) {
                                    logStr += "，无后续数据！\n";
                                    Log.i(TAG, logStr);
                                    try {
                                        //线程结束的时候流也关闭了，这里判断线程是否还存在写入
                                        if (_deviceInfoActivity._searchLogThreadFlag) {
                                            _deviceInfoActivity._logOutputStreamWriter.write("无后续数据\n");
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    MyProgressDialogUtil.DismissAlertDialog();
                                    return;
                                }
                                //流水号
                                int index = Integer.parseInt(strArray[11] + strArray[12], 16);
                                //前面补零
                                String indexStr = MyConvertUtil.AddZeroForNum(index + "", 4, true);
                                logStr += "流水号（Hex）：" + strArray[11] + strArray[12] + "（" + index + "）";
                                //事件产生时间
                                String yearStr = "20" + strArray[13];
                                int month = Integer.parseInt(strArray[14]);
                                //前面补零
                                String monthStr = MyConvertUtil.AddZeroForNum(month + "", 2, true);
                                int day = Integer.parseInt(strArray[15]);
                                //前面补零
                                String dayStr = MyConvertUtil.AddZeroForNum(day + "", 2, true);
                                int hour = Integer.parseInt(strArray[16]);
                                //前面补零
                                String hourStr = MyConvertUtil.AddZeroForNum(hour + "", 2, true);
                                int minute = Integer.parseInt(strArray[17]);
                                //前面补零
                                String minuteStr = MyConvertUtil.AddZeroForNum(minute + "", 2, true);
                                int second = Integer.parseInt(strArray[2]);
                                //前面补零
                                String secondStr = MyConvertUtil.AddZeroForNum(second + "", 2, true);
                                String timeStr = yearStr + "-" + monthStr + "-" + dayStr + " " + hourStr + ":" + minuteStr + ":" + secondStr;
                                logStr += "，产生时间：" + timeStr;
                                //事件
                                int eventIndex = Integer.parseInt(strArray[3], 16);
                                logStr += "，事件索引（Hex）：" + strArray[3] + "（" + eventIndex + "）";
                                String eventStr = "未知";
                                if (eventIndex < BleEvent.BLE_EVENT_ARRAY.length) {
                                    eventStr = BleEvent.BLE_EVENT_ARRAY[eventIndex];
                                }
                                logStr += "，事件：" + eventStr;
                                //事件产生时的参数
                                int param = Integer.parseInt(strArray[4], 16);
                                logStr += "，事件参数（Hex）：" + strArray[4] + "（" + param + "）";
                                //事件总数
                                _deviceInfoActivity._totalLogCount = Integer.parseInt(strArray[5] + strArray[6], 16);
                                logStr += "，有后续记录，目前记录数：" + _deviceInfoActivity._currentLogtCount + "，总数：" + _deviceInfoActivity._totalLogCount +
                                        "\n";
                                Log.i(TAG, logStr);
                                MyProgressDialogUtil.SetCircleProgressCurrent(_deviceInfoActivity._currentLogtCount);
                                MyProgressDialogUtil.SetCircleProgressMax(_deviceInfoActivity._totalLogCount);
                                try {
                                    //线程结束的时候流也关闭了，这里判断线程是否还存在写入
                                    if (_deviceInfoActivity._searchLogThreadFlag) {
                                        _deviceInfoActivity._logOutputStreamWriter.write(indexStr + "    " + timeStr + "    " + strArray[4] + " " + " " + "  " + eventStr + "\n");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                _deviceInfoActivity._currentLogtCount++;
                                _deviceInfoActivity._logStartOrContinue = 2;
                                MyProgressDialogUtil.SetDialogTitleTxt("导出第" + _deviceInfoActivity._currentLogtCount + "条日志...");
                                _deviceInfoActivity._txtDebug.append(logStr);
                            } catch (Exception e) {
                                try {
                                    //线程结束的时候流也关闭了，这里判断线程是否还存在写入
                                    if (_deviceInfoActivity._searchLogThreadFlag) {
                                        _deviceInfoActivity._logOutputStreamWriter.write("解析错误，数据：" + cmdStr + "\n");
                                    }
                                } catch (Exception e2) {
                                    e2.printStackTrace();
                                }
                            }
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                _deviceInfoActivity._txtDebug.append("\n" + e.toString() + "\n");
            } finally {
                int offset = _deviceInfoActivity._txtDebug.getLineCount() * _deviceInfoActivity._txtDebug.getLineHeight();
                if (offset > _deviceInfoActivity._txtDebug.getHeight()) {
                    _deviceInfoActivity._txtDebug.scrollTo(0, offset - _deviceInfoActivity._txtDebug.getHeight());
                }
            }
        }
    }

    private void InitListener() {
        _connectListener = new MyBleConnectListener() {
            @Override
            public void OnConnected() {
                Log.i(TAG, "成功建立连接");
                _isConnected = true;
                MyProgressDialogUtil.DismissAlertDialog();
                SetConnectSuccess(true);
                //返回时告知该设备已成功连接
                setResult(2, new Intent());
                StartTimerTask();
            }

            @Override
            public void OnConnecting() {
                Log.i(TAG, "正在建立连接...");
                MyProgressDialogUtil.ShowWaittingDialog(DeviceInfoActivity.this, true, null, "正在连接...\n如长时间未连\n接请返回重试");
            }

            @Override
            public void OnDisConnected() {
                MyProgressDialogUtil.DismissAlertDialog();
                SetConnectSuccess(false);
                _isConnected = false;
                Log.e(TAG, "连接已断开");
                runOnUiThread(() -> MyProgressDialogUtil.ShowWarning(DeviceInfoActivity.this, "知道了", "警告", "连接已断开，请检查设备然后重新连接！", true, () -> {
//                    //返回时重新刷新
//                    Intent intent = new Intent(DeviceInfoActivity.this, ListActivity.class);
//                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                    startActivity(intent);
                    finish();
                }));
            }
        };

        _messageListener = new MyBleMessageListener() {
            @Override
            public void OnWriteSuccess(byte[] byteArray) {
            }

            @Override
            public void OnReadSuccess(byte[] byteArray) {
                String result = MyConvertUtil.ByteArrayToHexStr(byteArray);
                result = MyConvertUtil.StrAddCharacter(result, 2, " ");
                Log.i(TAG, "接收：" + result);
                String[] strArray = result.split(" ");
                //目前最短的指令为13位
                if (strArray.length < 12) {
                    String loStr = "指令长度" + strArray.length + "错误，不予解析！";
                    _myHandler.obtainMessage(-99, "\n" + loStr + "\n").sendToTarget();
                    return;
                }
                String type = strArray[8].toUpperCase();
                //版本信息及内部时间
                if ("A0".equals(type)) {
                    _myHandler.obtainMessage(20, strArray).sendToTarget();
                } else if ("E0".equals(type)) {
                    Log.e(TAG, "版本信息及内部时间响应错误，不予解析！");
                    _myHandler.obtainMessage(-99, "\n版本信息及内部时间响应错误，不予解析！\n").sendToTarget();
                }
                //电池电量
                else if ("A1".equals(type)) {
                    _myHandler.obtainMessage(21, strArray).sendToTarget();
                } else if ("E1".equals(type)) {
                    String logStr = "电池电量响应错误，不予解析！";
                    Log.e(TAG, logStr);
                    _myHandler.obtainMessage(-99, "\n" + logStr + "\n").sendToTarget();
                }
                //状态及连接的标签数量
                else if ("A2".equals(type)) {
                    _myHandler.obtainMessage(22, strArray).sendToTarget();
                } else if ("E2".equals(type)) {
                    Log.e(TAG, "状态及连接的标签数量响应错误，不予解析！");
                    _myHandler.obtainMessage(-99, "\n状态及连接的标签数量响应错误，不予解析！\n").sendToTarget();
                }
                //相对位置信息
                else if ("A3".equals(type)) {
                    _myHandler.obtainMessage(23, strArray).sendToTarget();
                } else if ("E3".equals(type)) {
                    Log.e(TAG, "位置信息响应错误，不予解析！");
                    _myHandler.obtainMessage(-99, "\n位置信息响应错误，不予解析！\n").sendToTarget();
                }
                //内部日志
                else if ("AA".equals(type)) {
                    _myHandler.obtainMessage(42, strArray).sendToTarget();
                } else if ("EA".equals(type)) {
                    String logStr = "内部日志响应错误，不予解析，并结束本次查询！";
                    Log.e(TAG, logStr);
                    _myHandler.obtainMessage(-99, "\n" + logStr + "\n").sendToTarget();
                    try {
                        //线程结束的时候流也关闭了，这里判断线程是否还存在写入
                        if (_searchLogThreadFlag && null != _logOutputStreamWriter) {
                            _logOutputStreamWriter.write("内部日志响应错误，已停止！\n");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, e.getMessage());
                    } finally {
                        MyProgressDialogUtil.DismissAlertDialog();
                    }
                }
                //GPS位置
                else if ("B3".equals(type)) {
                    _myHandler.obtainMessage(33, strArray).sendToTarget();
                } else if ("F3".equals(type)) {
                    Log.e(TAG, "GPS位置响应错误，不予解析！");
                    _myHandler.obtainMessage(-99, "\nGPS位置响应错误，不予解析！\n").sendToTarget();
                }
                //传感器的测量数据
                else if ("BA".equals(type)) {
                    _myHandler.obtainMessage(40, strArray).sendToTarget();
                } else if ("FA".equals(type)) {
                    Log.e(TAG, "传感器的测量响应错误，不予解析！");
                    _myHandler.obtainMessage(-99, "\n传感器的测量响应错误，不予解析！\n").sendToTarget();
                }
            }
        };
    }

    private void Task() {
        Log.i(TAG, "定时任务执行...");
        try {
            MyBleUtil.SendComm(CMD_20);
            Thread.sleep(50);
            MyBleUtil.SendComm(CMD_21);
            Thread.sleep(50);
            MyBleUtil.SendComm(CMD_22);
            Thread.sleep(50);
            MyBleUtil.SendComm(CMD_23);
            Thread.sleep(50);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void SetConnectSuccess(boolean flag) {
        runOnUiThread(() -> {
            _isConnected = flag;
            if (flag) {
                _txtName.setTextColor(SPRING_GREEN);
                _txtAddress.setTextColor(SPRING_GREEN);
                _txtVersion.setTextColor(SPRING_GREEN);
                _txtTime.setTextColor(SPRING_GREEN);
                _txtState.setTextColor(SPRING_GREEN);
                _txtLabelNum.setTextColor(SPRING_GREEN);
                _txtX2.setTextColor(SPRING_GREEN);
                _txtY2.setTextColor(SPRING_GREEN);
                _txtZ2.setTextColor(SPRING_GREEN);
                _txtRssi.setTextColor(SPRING_GREEN);
                _txtType.setTextColor(SPRING_GREEN);
                _txtLocationUnit.setTextColor(SPRING_GREEN);
                _txtElec.setTextColor(SPRING_GREEN);
                _txtVol.setTextColor(SPRING_GREEN);
                _txtElecState.setTextColor(SPRING_GREEN);
            } else {
                _txtName.setTextColor(Color.GRAY);
                _txtAddress.setTextColor(Color.GRAY);
                _txtVersion.setTextColor(Color.GRAY);
                _txtTime.setTextColor(Color.GRAY);
                _txtState.setTextColor(Color.GRAY);
                _txtLabelNum.setTextColor(Color.GRAY);
                _txtX2.setTextColor(Color.GRAY);
                _txtY2.setTextColor(Color.GRAY);
                _txtZ2.setTextColor(Color.GRAY);
                _txtRssi.setTextColor(Color.GRAY);
                _txtType.setTextColor(Color.GRAY);
                _txtLocationUnit.setTextColor(Color.GRAY);
                _txtElec.setTextColor(Color.GRAY);
                _txtVol.setTextColor(Color.GRAY);
                _txtElecState.setTextColor(Color.GRAY);
            }
        });
    }

    /**
     * 取消定时查询任务
     */
    private void CancelTimerTask() {
        if (null != _timer)
            _timer.cancel();
        if (null != _timerTask)
            _timerTask.cancel();
        _taskIsRuning = false;
    }

    /**
     * 重新启动定时任务
     */
    private void StartTimerTask() {
        //避免重复启动定时任务
        if (!_taskIsRuning && _isConnected) {
            if (null != _timer)
                _timer.cancel();
            if (null != _timerTask)
                _timerTask.cancel();
            runOnUiThread(() -> _btnDebug.setText("停止调试"));
            _timer = new Timer();
            _timerTask = new TimerTask() {
                @Override
                public void run() {
                    Task();
                }
            };
            _timer.schedule(_timerTask, 0, TASK_TIME);
            _taskIsRuning = true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (!_isConnected) {
            Toast.makeText(this, "请先连接终端", Toast.LENGTH_SHORT).show();
            return false;
        }
        switch (item.getItemId()) {
            case R.id.item1_menu:
                if (item.getTitle().equals("打开调试")) {
                    _llDebug.setVisibility(View.VISIBLE);
                    item.setTitle("关闭调试");
                    StartTimerTask();
                    _btnDebug.setText("停止调试");
                } else {
                    item.setTitle("打开调试");
                    _llDebug.setVisibility(View.INVISIBLE);
                }
                break;
            case R.id.item2_menu:
                startActivityForResult(new Intent(this, DeviceSettingActivity.class), 101);
                break;
            case R.id.item3_menu:
                //TODO:不加这一句下次弹出窗口会有异常，为啥？
                MyProgressDialogUtil.DismissAlertDialog();
                //每次导出都要重置状态
                _searchLogThreadFlag = true;
                _taskIsRuning = false;
                _logStartOrContinue = 1;
                _currentLogtCount = 0;
                _totalLogCount = 0;
                //取消定时查询任务
                CancelTimerTask();
                Calendar calendar = Calendar.getInstance();
                String logPath =
                        "/sdcard/基站扫描内部日志" + calendar.get(Calendar.YEAR) + "年" + (calendar.get(Calendar.MONTH) + 1) + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日.txt";
                try {
                    MyFileUtil.DeleteFile(logPath);
                    MyFileUtil.MakeFile(logPath);
                    _logFileOutputStream = new FileOutputStream(new File(logPath), true);
                    _logOutputStreamWriter = new OutputStreamWriter(_logFileOutputStream, "UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                    MyProgressDialogUtil.ShowWarning(this, "知道了", "错误", e.getMessage(), false, null);
                    return true;
                }
                //弹出提示窗口
                MyProgressDialogUtil.ShowCircleProgressDialog(this, true, () -> {
                    try {
                        if (null != _logOutputStreamWriter) {
                            _logOutputStreamWriter.flush();
                            _logOutputStreamWriter.close();
                        }
                        if (_currentLogtCount >= _totalLogCount)
                            MyProgressDialogUtil.ShowWarning(this, "知道了", "提示", "日志成功导出！\n路径：" + logPath, false, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        MyProgressDialogUtil.DismissAlertDialog();
                        MyProgressDialogUtil.ShowWarning(this, "知道了", "错误", e.getMessage(), false, null);
                    } finally {
                        //停止导出日志的线程
                        _searchLogThreadFlag = false;
                        //恢复定时查询任务
                        StartTimerTask();
                    }
                }, "正在导出内部日志...\n点击可停止");
                _searchLogThread = new Thread(() -> {
                    while (_searchLogThreadFlag && _isConnected) {
                        //                        try {
                        //                            Thread.sleep(100);
                        //                        } catch (InterruptedException e) {
                        //                            e.printStackTrace();
                        //                        }
                        switch (_logStartOrContinue) {
                            case 1:
                                _logStartOrContinue = 2;
                                MyBleUtil.SendComm(CMD_2A_START);
                                Log.i(TAG, "开始查询内部日志...");
                                runOnUiThread(() -> _txtDebug.append("开始查询内部日志" + MyConvertUtil.StrAddCharacter(CMD_2A_START, 2, " ") + "\n"));
                                break;
                            case 2:
                                _logStartOrContinue = -1;
                                MyBleUtil.SendComm(CMD_2A_CONTINUE);
                                Log.i(TAG, "继续查询内部日志...");
                                runOnUiThread(() -> _txtDebug.append("继续查询内部日志" + MyConvertUtil.StrAddCharacter(CMD_2A_CONTINUE, 2, " ") + "\n"));
                                break;
                        }
                    }
                });
                _searchLogThread.start();
                break;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_info_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (null == data) {
            return;
        }
        if (resultCode == RESULT_OK) {
            String str = data.getStringExtra(ARG_PARAM1);
            Log.i(TAG, "返回时携带的数据：" + str);
            switch (requestCode) {
                case 101:
                    if ("已执行关机指令".equals(str)) {
                        MyBleUtil.DisConnGatt();
                        SetConnectSuccess(false);
                    }
                    break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MyBleUtil.SetMessageListener(_messageListener);
        StartTimerTask();
        _btnDebug.setText("停止调试");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != _timer)
            _timer.cancel();
        if (null != _timerTask)
            _timerTask.cancel();
        _taskIsRuning = false;
        _isConnected = false;
        MyBleUtil.DisConnGatt();
        _bluetoothDevice = null;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (null != _timer)
            _timer.cancel();
        if (null != _timerTask)
            _timerTask.cancel();
        _taskIsRuning = false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            MyBleUtil.DisConnGatt();
            finish();
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_return_info:
                if (null != _timer)
                    _timer.cancel();
                if (null != _timerTask)
                    _timerTask.cancel();
                _taskIsRuning = false;
                MyBleUtil.DisConnGatt();
                finish();
                break;
            case R.id.btn_top_info:
                _txtDebug.scrollTo(0, 0);
                break;
            case R.id.btn_bottom_info:
                int offset = _txtDebug.getLineCount() * _txtDebug.getLineHeight();
                if (offset > _txtDebug.getHeight()) {
                    _txtDebug.scrollTo(0, offset - _txtDebug.getHeight());
                }
                break;
            case R.id.btn_clear_info:
                _txtDebug.setText("");
                break;
            case R.id.btn_debug_info:
                if ("停止调试".equals(_btnDebug.getText().toString())) {
                    _btnDebug.setText("开始调试");
                    _timer.cancel();
                    _timerTask.cancel();
                    _taskIsRuning = false;
                } else {
                    _btnDebug.setText("停止调试");
                    StartTimerTask();
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);
        _myHandler = new MyHandler(this);
        Intent intent = getIntent();
        _bluetoothDevice = intent.getParcelableExtra(ARG_PARAM1);
        _uuidMap = (Map<String, UUID>) intent.getSerializableExtra(ARG_PARAM2);
        _llDebug = findViewById(R.id.ll_debug_info);
        _llDebug.setVisibility(View.INVISIBLE);
        _btnReturn = findViewById(R.id.btn_return_info);
        _btnReturn.setOnClickListener(this::onClick);
        _btnTop = findViewById(R.id.btn_top_info);
        _btnTop.setOnClickListener(this::onClick);
        _btnBottom = findViewById(R.id.btn_bottom_info);
        _btnBottom.setOnClickListener(this::onClick);
        _btnClear = findViewById(R.id.btn_clear_info);
        _btnClear.setOnClickListener(this::onClick);
        _btnDebug = findViewById(R.id.btn_debug_info);
        _btnDebug.setOnClickListener(this::onClick);
        //Toolbar
        _toolbar = findViewById(R.id.toolbar_info);
        _toolbar.setTitle("");
        setSupportActionBar(_toolbar);
        _txtName = findViewById(R.id.txt_name_info);
        _txtName.setText(_bluetoothDevice.getName());
        _txtAddress = findViewById(R.id.txt_address_info);
        _txtAddress.setText(_bluetoothDevice.getAddress());
        _txtVersion = findViewById(R.id.txt_version_info);
        _txtTime = findViewById(R.id.txt_time_info);
        _txtState = findViewById(R.id.txt_state_info);
        _txtLabelNum = findViewById(R.id.txt_label_num_info);
        _txtX2 = findViewById(R.id.txt_x2_info);
        _txtY2 = findViewById(R.id.txt_y2_info);
        _txtZ2 = findViewById(R.id.txt_z2_info);
        _txtRssi = findViewById(R.id.txt_rssi_info);
        _txtType = findViewById(R.id.txt_type_info);
        _txtDebug = findViewById(R.id.txt_debug_info);
        _txtDebug.setMovementMethod(ScrollingMovementMethod.getInstance());
        _txtLocationUnit = findViewById(R.id.txt_location_unit_info);
        _txtElec = findViewById(R.id.txt_elec_info);
        _txtVol = findViewById(R.id.txt_vol_info);
        _txtElecState = findViewById(R.id.txt_elec_state_info);
        InitListener();
        MyBleUtil.SetConnectListener(_connectListener);
        Log.i(TAG, "开始连接蓝牙...");
        MyBleUtil.ConnectDevice(_bluetoothDevice, _uuidMap);
    }

}
