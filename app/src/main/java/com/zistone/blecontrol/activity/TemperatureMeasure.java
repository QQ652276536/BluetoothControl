package com.zistone.blecontrol.activity;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.baidu.tts.chainofresponsibility.logger.LoggerProxy;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.zistone.blecontrol.R;
import com.zistone.blecontrol.baidutts.InitConfig;
import com.zistone.blecontrol.baidutts.MySyntherizer;
import com.zistone.blecontrol.baidutts.NonBlockSyntherizer;
import com.zistone.blecontrol.baidutts.util.Auth;
import com.zistone.blecontrol.baidutts.util.IOfflineResourceConst;
import com.zistone.blecontrol.baidutts.util.MessageListener;
import com.zistone.blecontrol.baidutts.util.OfflineResource;
import com.zistone.blecontrol.controls.AmountView;
import com.zistone.blecontrol.opencv.DetectionBasedTracker;
import com.zistone.blecontrol.util.BluetoothListener;
import com.zistone.blecontrol.util.BluetoothUtil;
import com.zistone.blecontrol.util.ConvertUtil;
import com.zistone.blecontrol.util.DeviceFilterShared;
import com.zistone.blecontrol.util.MyActivityManager;
import com.zistone.blecontrol.util.ProgressDialogUtil;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class TemperatureMeasure extends AppCompatActivity implements View.OnClickListener, BluetoothListener,
        CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "TemperatureMeasure";
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String SEARCH_TEMPERATURE_COMM1 = "680000000000006810000180E616";
    private static final String SEARCH_TEMPERATURE_COMM2 = "680000000000006810000181E116";
    private static final int MESSAGE_ERROR_1 = -1;
    private static final int MESSAGE_ERROR_2 = -2;
    private static final int MESSAGE_ERROR_3 = -3;
    private static final int MESSAGE_1 = 100;
    private static final int RECEIVE = 8002;

    private BluetoothDevice _bluetoothDevice;
    private Context _context;
    private Toolbar _toolbar;
    private ImageButton _btnReturn;
    private TextView _txt1, _txt2, _txt3, _txt4, _txt5;
    private StringBuffer _stringBuffer = new StringBuffer();
    private Map<String, UUID> _uuidMap;
    private ProgressDialogUtil.Listener _progressDialogUtilListener;
    //是否连接成功
    private boolean _connectedSuccess = false;
    private Timer _refreshTimer;
    private TimerTask _refreshTask;
    private AmountView _amountView;
    private AmountView.OnAmountChangeListener _onAmountChangeListener;
    //测量温度
    private double _measuringValue = 0.0;
    private int _calcCount = 3;
    private double[] _calcArray = new double[_calcCount];
    //TTS语音部分
    //发布时请替换成自己申请的_appId、_appKey和_secretKey
    //注意如果需要离线合成功能,请在您申请的应用中填写包名
    //发布时请替换成自己申请的appId、appKey和secretKey,注意如果需要离线合成功能,请在您申请的应用中填写包名
    private String _appId = "18730922";
    private String _appKey = "Dqm6IyZ47QXlX0WvHnrZKmsF";
    private String _secretKey = "UXM4raYA21UA7m48b49lGdEGLZOpIK3w";
    //纯离线合成SDK授权码;离在线合成SDK免费,没有此参数
    private String _sn;
    private TtsMode _ttsMode = IOfflineResourceConst.DEFAULT_OFFLINE_TTS_MODE;
    private String _offlineVoice = OfflineResource.VOICE_MALE;
    //主控制类,所有合成控制方法从这个类开始
    private MySyntherizer _mySyntherizer;
    //统计平均温度的次数,语音合成状态:-1表示合成或播放过程中出现错误,1表示合成结束,2表示开始播放,3表示播放结束
    private int _speechState = 3;
    //OpenCV部分
    //蓝牙设备连接成功以后再进行初始化
    //修改DetectionBasedTracker类里的deliverAndDrawFrame()方法可旋转角度
    //旋转角度后如果要重绘内容也得加上旋转角度
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0);
    private static final Scalar READ_COLOR = new Scalar(255, 0, 0);
    private static final int JAVA_DETECTOR = 0;
    private static final int NATIVE_DETECTOR = 1;
    private CameraBridgeViewBase _cameraView;
    //灰度图像,R、G、B彩色图像
    private Mat _gray, _rgba;
    private int _detectorType = NATIVE_DETECTOR, _absoluteFaceSize = 0;
    private float _relativeFaceSize = 0.2f;
    private DetectionBasedTracker _nativeDetector;
    private CascadeClassifier _javaDetector;
    private File _cascadeFile;
    private MediaPlayer _mediaPlayer1, _mediaPlayer2;

    private void InitListener() {
        _progressDialogUtilListener = new ProgressDialogUtil.Listener() {
            @Override
            public void OnDismiss() {
                if (!_connectedSuccess)
                    DisConnect();
            }
        };
        _onAmountChangeListener = new AmountView.OnAmountChangeListener() {
            @Override
            public void onAmountChange(View view, double current) {
                DeviceFilterShared.SetTemperatureParam(_context, String.valueOf(_amountView.getCurrent()));
            }
        };
    }

    private BaseLoaderCallback _baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    //OpenCV初始化加载成功,再加载本地so库
                    System.loadLibrary("opencv341");
                    try {
                        //加载人脸检测模式文件
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        _cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(_cascadeFile);
                        byte[] buffer = new byte[4096];
                        int byteesRead;
                        while ((byteesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, byteesRead);
                        }
                        is.close();
                        os.close();
                        //使用模型文件初始化人脸检测引擎
                        _javaDetector = new CascadeClassifier(_cascadeFile.getAbsolutePath());
                        if (_javaDetector.empty()) {
                            Log.e(TAG, "加载cascade classifier失败");
                            _javaDetector = null;
                        } else {
                            Log.i(TAG, "Loaded cascade classifier from " + _cascadeFile.getAbsolutePath());
                        }
                        _nativeDetector = new DetectionBasedTracker(_cascadeFile.getAbsolutePath(), "", 0);
                        cascadeDir.delete();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //开启渲染Camera
                    _cameraView.enableView();
                    break;
            }
            super.onManagerConnected(status);
        }
    };

    private Handler _speechStateHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            super.handleMessage(message);
            _speechState = message.what;
        }
    };

    private Handler _handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            super.handleMessage(message);
            String result = (String) message.obj;
            switch (message.what) {
                case MESSAGE_1: {
                    Speak("已连接");
                    //连接成功后再显示人脸检测
                    _cameraView.setVisibility(View.VISIBLE);
                    _refreshTimer = new Timer();
                    _refreshTask = new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                BluetoothUtil.SendComm(SEARCH_TEMPERATURE_COMM1);
                                //                                    Log.i(TAG, "发送查询温度的指令...");
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    //任务、延迟执行时间、重复调用间隔,Timer和TimerTask在调用cancel()取消后不能再执行schedule语句
                    _refreshTimer.schedule(_refreshTask, 0, 1 * 1000);
                }
                break;
                case RECEIVE: {
                    String strs[] = result.split(",");
                    //最高温度
                    double value1 = Double.valueOf(strs[0]) / 100;
                    //最低温度
                    double value2 = Double.valueOf(strs[1]) / 100;
                    //环境温度
                    double value3 = Double.valueOf(strs[2]) / 100;
                    //平均温度
                    double value4 = Double.valueOf(strs[3]) / 100;
                    _txt1.setText(value1 + "℃");
                    _txt1.setTextColor(Color.RED);
                    _txt2.setText(value2 + "℃");
                    _txt2.setTextColor(Color.YELLOW);
                    _txt3.setText(value3 + "℃");
                    _txt3.setTextColor(Color.BLUE);
                    _txt4.setText(value4 + "℃");
                    _txt4.setTextColor(Color.CYAN);
                    ReadySpeak(value1, value2, value3, value4);
                }
                break;
            }
        }
    };

    /**
     * 准备语音合成的内容
     *
     * @param value1 最高温度
     * @param value2 最低温度
     * @param value3 环境温度
     * @param value4 平均温度
     */
    private void ReadySpeak(double value1, double value2, double value3, double value4) {
        //统计3次,也相当于一个延时播报的处理吧
        if (_calcCount > 0) {
            _calcArray[(_calcCount - 3) * -1] = value1;
            if (_calcCount == 1) {
                double total = 0.0;
                for (double temp : _calcArray) {
                    total += temp;
                }
                _measuringValue = total / 3 + _amountView.getCurrent();
                //只保留两位小数
                String strAvValue = new DecimalFormat("0.0").format(_measuringValue);
                Log.i(TAG, String.format("最高温度:%s℃ 最低温度:%s℃ 环境温度:%s℃ 平均温度:%s℃ 测量温度:%s℃", value1, value2, value3, value4, strAvValue));
                _txt5.setText(strAvValue + "℃");
                _txt5.setTextColor(Color.GREEN);
                if (_measuringValue >= 34 && _measuringValue <= 37.5) {
                    _mediaPlayer1.start();
                } else if (_measuringValue > 37.5) {
                    _mediaPlayer2.start();
                }
                //上一条语音播放完毕才播放下一条
                //                if (_speechState == 3 && _measuringValue >= 34) {
                //                    Speak(strAvValue + "度");
                //                }
            }
            _calcCount--;
            if (_calcCount == 0) {
                _calcCount = 3;
                _calcArray = new double[_calcCount];
            }
        }
    }

    /**
     * 初始化引擎,需要的参数均在InitConfig类里
     */
    private void InitTTS() {
        //日志打印在logcat中
        LoggerProxy.printable(true);
        //语音合成时的日志
        MessageListener messageListener = new MessageListener(_speechStateHandler);
        SpeechSynthesizerListener listener = messageListener;
        //设置初始化参数
        InitConfig config = GetInitConfig(listener);
        _mySyntherizer = new NonBlockSyntherizer(_context, config);
    }

    /**
     * 合成的参数,可以初始化时填写,也可以在合成前设置.
     *
     * @return 合成参数
     */
    private Map<String, String> GetParams() {
        //以下参数均为选填
        Map<String, String> params = new HashMap<>();
        //设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>, 其它发音人见文档
        params.put(SpeechSynthesizer.PARAM_SPEAKER, "0");
        //设置合成的音量,0-15 ,默认 5
        params.put(SpeechSynthesizer.PARAM_VOLUME, "15");
        //设置合成的语速,0-15 ,默认 5
        params.put(SpeechSynthesizer.PARAM_SPEED, "5");
        //设置合成的语调,0-15 ,默认 5
        params.put(SpeechSynthesizer.PARAM_PITCH, "5");
        //MIX_MODE_DEFAULT                          默认 ,wifi状态下使用在线,非wifi离线.在线状态下,请求超时6s自动转离线
        //MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI       wifi状态下使用在线,非wifi离线.在线状态下, 请求超时1.2s自动转离线
        //MIX_MODE_HIGH_SPEED_NETWORK               3G 4G wifi状态下使用在线,其它状态离线.在线状态下,请求超时1.2s自动转离线
        //MIX_MODE_HIGH_SPEED_SYNTHESIZE            2G 3G 4G wifi状态下使用在线,其它状态离线.在线状态下,请求超时1.2s自动转离线
        params.put(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        //离在线模式,强制在线优先.在线请求后超时2秒后,转为离线合成.
        //params.put(SpeechSynthesizer.PARAM_MIX_MODE_TIMEOUT, SpeechSynthesizer.PARAM_MIX_TIMEOUT_TWO_SECOND);
        //离线资源文件, 从assets目录中复制到临时目录,需要在initTTs方法前完成
        OfflineResource offlineResource = CreateOfflineResource(_offlineVoice);
        //声学模型文件路径 (离线引擎使用), 请确认下面两个文件存在
        params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, offlineResource.getTextFilename());
        params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, offlineResource.getModelFilename());
        return params;
    }

    private InitConfig GetInitConfig(SpeechSynthesizerListener listener) {
        Map<String, String> params = GetParams();
        //添加你自己的参数
        InitConfig initConfig;
        if (_sn == null) {
            initConfig = new InitConfig(_appId, _appKey, _secretKey, _ttsMode, params, listener);
        } else {
            initConfig = new InitConfig(_appId, _appKey, _secretKey, _sn, _ttsMode, params, listener);
        }
        //上线时请删除AutoCheck的调用
        //       AutoCheck.getInstance(getApplicationContext()).check(initConfig, new Handler() {
        //           @Override
        //           public void handleMessage(Message msg) {
        //               if (msg.what == 100) {
        //                   AutoCheck autoCheck = (AutoCheck) msg.obj;
        //                   synchronized (autoCheck) {
        //                       String message = autoCheck.obtainDebugMessage();
        //                       Log.i(TAG, message);
        //                   }
        //               }
        //           }
        //
        //       });
        return initConfig;
    }

    /**
     * 复制assets里的离线资源文件到设备的/sdcard/baituTTS/
     *
     * @param voiceType
     * @return
     */
    private OfflineResource CreateOfflineResource(String voiceType) {
        OfflineResource offlineResource = null;
        try {
            offlineResource = new OfflineResource(this, voiceType);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "复制assets里的离线资源文件到设备路径的/sdcard/baituTTS/失败!!!\n" + e.getMessage());
        }
        return offlineResource;
    }

    /**
     * Speak实际上是调用Synthesize后获取音频流,然后播放.
     * 获取音频流的方式见SaveFileActivity及FileSaveListener
     *
     * @param text 需要合成的文本,长度不能超过1024个GBK字节.
     */
    private void Speak(String text) {
        int result = _mySyntherizer.Speak(text);
        CheckResult(result, "Speak()");
    }


    /**
     * 合成但是不播放
     * 音频流保存为文件的方法可以参见SaveFileActivity及FileSaveListener
     *
     * @param text 需要合成的文本,长度不能超过1024个GBK字节.
     */
    private void Synthesize(String text) {
        int result = _mySyntherizer.Synthesize(text);
        CheckResult(result, "Synthesize()");
    }

    /**
     * 批量播放
     */
    private void BatchSpeak() {
        List<Pair<String, String>> texts = new ArrayList<>();
        texts.add(new Pair<>("已成功连接设(she4)备(bei4)", "a0"));
        texts.add(new Pair<>("重(zhong4)量这个是多音字示例", "a1"));
        int result = _mySyntherizer.BatchSpeak(texts);
        CheckResult(result, "BatchSpeak()");
    }

    /**
     * 切换离线发音,引擎在合成时该方法不能调用
     *
     * @param mode
     */
    private void LoadModel(String mode) {
        _offlineVoice = mode;
        OfflineResource offlineResource = CreateOfflineResource(_offlineVoice);
        Log.i(TAG, "切换离线语音:" + offlineResource.getModelFilename());
        int result = _mySyntherizer.LoadModel(offlineResource.getModelFilename(), offlineResource.getTextFilename());
        CheckResult(result, "LoadModel()");
    }

    private void CheckResult(int result, String method) {
        if (result != 0) {
            Log.e(TAG, String.format("方法%s执行失败,错误代码:%s", method, result));
        }
    }

    /**
     * 暂停播放,仅调用Speak()后生效
     */
    private void Pause() {
        int result = _mySyntherizer.Pause();
        CheckResult(result, "Pause()");
    }

    /**
     * 继续播放,仅调用Speak()后再调用Pause()生效
     */
    private void Resume() {
        int result = _mySyntherizer.Resume();
        CheckResult(result, "Resume()");
    }

    /**
     * 停止合成引擎,即停止播放、合成、清空内部合成队列
     */
    private void Stop() {
        int result = _mySyntherizer.Stop();
        CheckResult(result, "Stop()");
    }

    /**
     * 断开与BLE设备的连接
     */
    private void DisConnect() {
        if (_refreshTask != null) {
            _refreshTask.cancel();
        }
        if (_refreshTimer != null) {
            _refreshTimer.cancel();
        }
        BluetoothUtil.DisConnGatt();
        _txt1.setText("Null");
        _txt1.setTextColor(Color.GRAY);
        _txt2.setText("Null");
        _txt2.setTextColor(Color.GRAY);
        _txt3.setText("Null");
        _txt3.setTextColor(Color.GRAY);
        _txt4.setText("Null");
        _txt4.setTextColor(Color.GRAY);
        _txt5.setText("Null");
        _txt5.setTextColor(Color.GRAY);
        Speak("连接已断开");
    }

    /**
     * 解析硬件返回的数据
     *
     * @param data
     */
    private void Resolve(String data) {
        Log.i(TAG, "共接收:" + data);
        String[] strArray = data.split(" ");
        String indexStr = strArray[12];
        Message message = new Message();
        switch (indexStr) {
            case "80": {
                byte[] bytes1 = ConvertUtil.HexStrToByteArray(strArray[13]);
                String bitStr = ConvertUtil.ByteToBit(bytes1[0]);
                String doorState1 = String.valueOf(bitStr.charAt(7));
                String lockState1 = String.valueOf(bitStr.charAt(6));
                String doorState2 = String.valueOf(bitStr.charAt(5));
                String lockState2 = String.valueOf(bitStr.charAt(4));
                //强磁开关状态
                String magneticState = String.valueOf(bitStr.charAt(3));
                //外接电源状态
                String outsideState = String.valueOf(bitStr.charAt(2));
                //内部电池充电状态
                String insideState = String.valueOf(bitStr.charAt(1));
                //电池电量(平均温度)
                int battery = Integer.parseInt(strArray[14] + strArray[15], 16);
                //下端磁强(最低温度)
                int magneticDown = Integer.parseInt(strArray[16] + strArray[17], 16);
                //上端磁强(最高温度)
                int magneticUp = Integer.parseInt(strArray[2] + strArray[3], 16);
                //前端磁强(环境温度)
                int magneticBefore = Integer.parseInt(strArray[4] + strArray[5], 16);
                message.what = RECEIVE;
                //注意几个温度的顺序,最高->最低->环境->平均,后面解析也是这个顺序来的
                message.obj = magneticUp + "," + magneticDown + "," + magneticBefore + "," + battery;
            }
            break;
        }
        _handler.sendMessage(message);
    }

    @Override
    public void OnConnected() {
        ProgressDialogUtil.Dismiss();
        Log.i(TAG, "成功建立连接!");
        //轮询
        Message message = _handler.obtainMessage(MESSAGE_1, "");
        _handler.sendMessage(message);
        //返回时告知该设备已成功连接
        setResult(2, new Intent());
        _connectedSuccess = true;
    }

    @Override
    public void OnConnecting() {
        ProgressDialogUtil.ShowProgressDialog(_context, _progressDialogUtilListener, "正在连接...");
    }

    @Override
    public void OnDisConnected() {
        Log.i(TAG, "连接已断开!");
        _connectedSuccess = false;
    }

    @Override
    public void OnWriteSuccess(byte[] byteArray) {
        String result = ConvertUtil.ByteArrayToHexStr(byteArray);
        result = ConvertUtil.HexStrAddCharacter(result, " ");
        String[] strArray = result.split(" ");
        String indexStr = strArray[11];
        switch (indexStr) {
        }
    }

    @Override
    public void OnReadSuccess(byte[] byteArray) {
        String result = ConvertUtil.ByteArrayToHexStr(byteArray);
        result = ConvertUtil.HexStrAddCharacter(result, " ");
        //Log.i(TAG, "接收:" + result);
        String[] strArray = result.split(" ");
        //一个包(20个字节)
        if (strArray[0].equals("68") && strArray[strArray.length - 1].equals("16")) {
            Resolve(result);
            //清空缓存
            _stringBuffer = new StringBuffer();
        }
        //分包
        else {
            if (!strArray[strArray.length - 1].equals("16")) {
                _stringBuffer.append(result + " ");
            }
            //最后一个包
            else {
                _stringBuffer.append(result);
                result = _stringBuffer.toString();
                Resolve(result);
                //清空缓存
                _stringBuffer = new StringBuffer();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            this.finish();
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_return_temperature: {
                finish();
            }
            break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temperature_measure);
        _context = MyActivityManager.getInstance().GetCurrentActivity();
        _mediaPlayer1 = MediaPlayer.create(this, R.raw.dingdong);
        _mediaPlayer2 = MediaPlayer.create(this, R.raw.didi);
        //初始化TTS引擎
        InitTTS();
        Intent intent = getIntent();
        _bluetoothDevice = intent.getParcelableExtra(ARG_PARAM1);
        _uuidMap = (Map<String, UUID>) intent.getSerializableExtra(ARG_PARAM2);
        //Toolbar
        _toolbar = findViewById(R.id.toolbar_temperature);
        _toolbar.setTitle("");
        setSupportActionBar(_toolbar);
        _btnReturn = findViewById(R.id.btn_return_temperature);
        _txt1 = findViewById(R.id.txt1_temperature);
        _txt2 = findViewById(R.id.txt2_temperature);
        _txt3 = findViewById(R.id.txt3_temperature);
        _txt4 = findViewById(R.id.txt4_temperature);
        _txt5 = findViewById(R.id.txt5_temperature);
        _btnReturn.setOnClickListener(this::onClick);
        _amountView = findViewById(R.id.amountView_temperature);
        _amountView.setMax(10);
        _amountView.setMin(-10);
        _amountView.setStep(0.1);
        _amountView.setCurrent(Double.valueOf(DeviceFilterShared.GetTemperatureParam(_context)));
        _cameraView = findViewById(R.id.cameraView_face);
        //前置摄像头CameraBridgeViewBase.CAMERA_ID_FRONT
        //后置摄像头CameraBridgeViewBase.CAMERA_ID_BACK
        _cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        _cameraView.setCvCameraViewListener(TemperatureMeasure.this);
        InitListener();
        //初始化完再设置监听
        _amountView.setLister(_onAmountChangeListener);
        BluetoothUtil.Init(_context, this);
        if (_bluetoothDevice != null) {
            Log.i(TAG, "开始连接...");
            BluetoothUtil.ConnectDevice(_bluetoothDevice, _uuidMap);
        } else {
            ProgressDialogUtil.ShowWarning(_context, "警告", "未获取到蓝牙,请重试!");
        }
        try {
            Auth.getInstance(this);
        } catch (Auth.AuthCheckException e) {
            Log.e(TAG, e.getMessage());
            return;
        }
    }

    @Override
    protected void onResume() {
        //静态初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "无法加载OpenCV本地库,将使用OpenCV Manager初始化");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, _baseLoaderCallback);
        } else {
            Log.i(TAG, "成功加载OpenCV本地库");
            _baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        //停止渲染Camera
        if (_cameraView != null)
            _cameraView.disableView();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (_refreshTask != null) {
            _refreshTask.cancel();
        }
        if (_refreshTimer != null) {
            _refreshTimer.cancel();
        }
        if (_mySyntherizer != null) {
            _mySyntherizer.Stop();
            _mySyntherizer.Release();
        }
        //停止渲染Camera
        if (_cameraView != null)
            _cameraView.disableView();
        if (_mediaPlayer1 != null)
            _mediaPlayer1.release();
        if (_mediaPlayer2 != null)
            _mediaPlayer2.release();
        BluetoothUtil.DisConnGatt();
        _bluetoothDevice = null;
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        _gray = new Mat();
        _rgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        _gray.release();
        _rgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        _rgba = inputFrame.rgba();
        _gray = inputFrame.gray();
        //设置脸部大小
        if (_absoluteFaceSize == 0) {
            int height = _gray.rows();
            if (Math.round(height * _relativeFaceSize) > 0) {
                _absoluteFaceSize = Math.round(height * _relativeFaceSize);
            }
            _nativeDetector.setMinFaceSize(_absoluteFaceSize);
        }
        //获取检测到的脸部数据
        MatOfRect faces = new MatOfRect();
        if (_detectorType == JAVA_DETECTOR) {
            if (_javaDetector != null) {
                _javaDetector.detectMultiScale(_gray, faces, 1.1, 2, 2, new Size(_absoluteFaceSize, _absoluteFaceSize), new Size());
            }
        } else if (_detectorType == NATIVE_DETECTOR) {
            if (_nativeDetector != null) {
                _nativeDetector.detect(_gray, faces);
            }
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }
        Rect[] facesArray = faces.toArray();
        //绘制检测框
        for (int i = 0; i < facesArray.length; i++) {
            Imgproc.rectangle(_rgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
        }
        int a = _rgba.cols();
        int b = _rgba.rows();
        Point point1 = new Point(0, 0);
        Point point2 = new Point(a, b);
        if (_measuringValue >= 34 && _measuringValue <= 37.5) {
            Imgproc.rectangle(_rgba, point1, point2, FACE_RECT_COLOR, 80);
        } else if (_measuringValue > 37.5) {
            Imgproc.rectangle(_rgba, point1, point2, READ_COLOR, 80);
        }
        //绘制文字
        //Imgproc.putText(_rgba, "36.78", point, 36, 1, FACE_RECT_COLOR);
        return _rgba;
    }

}