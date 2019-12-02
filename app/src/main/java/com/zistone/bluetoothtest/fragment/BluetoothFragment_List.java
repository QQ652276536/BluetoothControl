package com.zistone.bluetoothtest.fragment;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.zistone.bluetoothtest.R;
import com.zistone.bluetoothtest.control.BluetoothListAdapter;
import com.zistone.material_refresh_layout.MaterialRefreshLayout;
import com.zistone.material_refresh_layout.MaterialRefreshListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BluetoothFragment_List extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener, CompoundButton.OnCheckedChangeListener, RadioGroup.OnCheckedChangeListener
{
    public static final String TAG = "BluetoothFragment_List";
    public static final String ARG_PARAM1 = "param1";
    public static final String ARG_PARAM2 = "param2";
    //已知服务
    private static UUID SERVICE_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    //写入特征的UUID
    private static UUID WRITE_UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb");
    //读取特征的UUID
    private static UUID READ_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    //客户端特征配置
    private static UUID CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public String m_param1;
    public String m_param2;
    public Context m_context;
    public View m_view;
    public OnFragmentInteractionListener m_listener;
    public CheckBox m_checkBox;
    public TextView m_textView1;
    public ListView m_listView;
    public BluetoothAdapter m_bluetoothAdapter;
    public ArrayList<BluetoothDevice> m_deviceList = new ArrayList<>();
    public BluetoothReceiver m_bluetoothReceiver;
    public BluetoothDevice m_bluetoothDevice;
    public BluetoothFragment_ReadWrite m_bluetoothFragment_readWrite;
    public BluetoothFragment_PowerControl m_bluetoothFragment_powerControl;
    //下拉刷新控件
    private MaterialRefreshLayout m_materialRefreshLayout;
    private CheckBox m_checkBox1;
    private RadioGroup m_radioGroup;
    private long m_exitTime = 0;

    public static BluetoothFragment_List newInstance(String param1, String param2)
    {
        BluetoothFragment_List fragment = new BluetoothFragment_List();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public class BluetoothReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            // 获得已经搜索到的蓝牙设备
            if(action.equals(BluetoothDevice.ACTION_FOUND))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(!m_deviceList.contains(device.getAddress()))
                {
                    m_deviceList.add(device);
                }
                BluetoothListAdapter adapter = new BluetoothListAdapter(m_context, m_deviceList);
                m_listView.setAdapter(adapter);
                m_listView.setOnItemClickListener(BluetoothFragment_List.this);
            }
            else if(action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
            {
                handler.removeCallbacks(runnable);
                m_textView1.setText("蓝牙设备搜索完成");
            }
            else if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getBondState() == BluetoothDevice.BOND_BONDING)
                {
                    m_textView1.setText("正在配对" + device.getName());
                }
                else if(device.getBondState() == BluetoothDevice.BOND_BONDED)
                {
                    m_textView1.setText("完成配对" + device.getName());
                    handler.postDelayed(runnable, 50);
                }
                else if(device.getBondState() == BluetoothDevice.BOND_NONE)
                {
                    m_textView1.setText("取消配对" + device.getName());
                }
            }
        }
    }

    public Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if(msg.what == 0)
            {
                byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.d(TAG, "handleMessage readMessage=" + readMessage);
                AlertDialog.Builder builder = new AlertDialog.Builder(m_context);
                builder.setTitle("我收到消息啦").setMessage(readMessage).setPositiveButton("确定", null);
                builder.create().show();
            }
        }
    };

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId)
    {
        switch(checkedId)
        {
            case R.id.radioButton1:
                SERVICE_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
                WRITE_UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb");
                READ_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
                break;
            case R.id.radioButton2:
                SERVICE_UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e1011");
                WRITE_UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e0011");
                READ_UUID = UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e0012");
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if(buttonView.getId() == R.id.ck_bluetooth)
        {
            if(isChecked == true)
            {
                BeginDiscovery();
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(intent, 1);
            }
            else
            {
                CancelDiscovery();
                m_bluetoothAdapter.disable();
                m_deviceList.clear();
                BluetoothListAdapter adapter = new BluetoothListAdapter(m_context, m_deviceList);
                m_listView.setAdapter(adapter);
            }
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();
        handler.postDelayed(runnable, 100);
        m_bluetoothReceiver = new BluetoothReceiver();
        //需要过滤多个动作，则调用IntentFilter对象的addAction添加新动作
        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        foundFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        foundFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        m_context.registerReceiver(m_bluetoothReceiver, foundFilter);
    }

    /**
     * 重写onRequestPermissionsResult方法
     * 获取动态权限请求的结果,再开启蓝牙
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if(requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            switch(m_bluetoothAdapter.getState())
            {
                case BluetoothAdapter.STATE_ON:
                case BluetoothAdapter.STATE_TURNING_ON:
                    m_checkBox.setChecked(true);
                    break;
                case BluetoothAdapter.STATE_OFF:
                case BluetoothAdapter.STATE_TURNING_OFF:
                default:
                    m_checkBox.setChecked(false);
                    break;
            }
            m_checkBox.setOnCheckedChangeListener(this);
            m_textView1.setOnClickListener(this);
            m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(m_bluetoothAdapter == null)
            {
                Toast.makeText(m_context, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            }
        }
        else
        {
            Toast.makeText(m_context, "用户拒绝了权限", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onClick(View v)
    {
        if(v.getId() == R.id.tv_discovery)
        {
            BeginDiscovery();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        //连接设备前先关闭扫描蓝牙,否则连接成功后再次扫描会发生阻塞,导致扫描不到设备
        CancelDiscovery();
        m_bluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_deviceList.get(position).getAddress());
        Map<String, UUID> map = new HashMap<>();
        map.put("SERVICE_UUID", SERVICE_UUID);
        map.put("READ_UUID", READ_UUID);
        map.put("WRITE_UUID", WRITE_UUID);
        map.put("CONFIG_UUID", CONFIG_UUID);
        if(m_bluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE)
        {
            //停止搜索蓝牙
            CancelDiscovery();
            if(!m_checkBox1.isChecked())
            {
                m_bluetoothFragment_readWrite = BluetoothFragment_ReadWrite.newInstance(m_bluetoothDevice, map);
                //不要使用replace,不然前面的Fragment被释放了会连蓝牙也关掉
                getFragmentManager().beginTransaction().add(R.id.fragment_bluetooth, m_bluetoothFragment_readWrite, "bluetoothFragment_readWrite").commitNow();
                getFragmentManager().beginTransaction().hide(BluetoothFragment_List.this).commitNow();
            }
            else
            {
                m_bluetoothFragment_powerControl = BluetoothFragment_PowerControl.newInstance(m_bluetoothDevice, map);
                //不要使用replace,不然前面的Fragment被释放了会连蓝牙也关掉
                getFragmentManager().beginTransaction().add(R.id.fragment_bluetooth, m_bluetoothFragment_powerControl, "bluetoothFragment_powerControl").commitNow();
                getFragmentManager().beginTransaction().hide(BluetoothFragment_List.this).commitNow();
            }
        }
        else
        {
            Toast.makeText(m_context, "请检查该设备是否被占用", Toast.LENGTH_SHORT);
        }
    }

    /**
     * Activity中加载Fragment时会要求实现onFragmentInteraction(Uri uri)方法,此方法主要作用是从fragment向activity传递数据
     */
    public interface OnFragmentInteractionListener
    {
        void onFragmentInteraction(Uri uri);
    }

    public void onButtonPressed(Uri uri)
    {
        if(m_listener != null)
        {
            m_listener.onFragmentInteraction(uri);
        }
    }

    /**
     * 异步搜索蓝牙设备
     */
    public Runnable runnable = new Runnable()
    {
        @Override
        public void run()
        {
            BeginDiscovery();
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * 开始搜索蓝牙
     */
    public void BeginDiscovery()
    {
        if(m_bluetoothAdapter.isDiscovering() != true)
        {
            m_deviceList.clear();
            BluetoothListAdapter adapter = new BluetoothListAdapter(m_context, m_deviceList);
            m_listView.setAdapter(adapter);
            m_textView1.setText("正在搜索蓝牙设备");
            //startDiscovery虽然兼容经典蓝牙和低功耗蓝牙,但有些设备无法检测到低功耗蓝牙
            m_bluetoothAdapter.startDiscovery();
        }
    }

    /**
     * 取消搜索蓝牙
     */
    public void CancelDiscovery()
    {
        handler.removeCallbacks(runnable);
        m_textView1.setText("取消搜索蓝牙设备");
        if(m_bluetoothAdapter.isDiscovering() == true)
        {
            m_bluetoothAdapter.cancelDiscovery();
        }
    }

    private View.OnKeyListener backListener = (v, keyCode, event) ->
    {
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN)
        {
            if((System.currentTimeMillis() - m_exitTime) > 2000)
            {
                Toast.makeText(getActivity(), "再按一次退出程序", Toast.LENGTH_SHORT).show();
                m_exitTime = System.currentTimeMillis();
            }
            else
            {
                getActivity().finish();
                System.exit(0);
            }
            return true;
        }
        return false;
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if(getArguments() != null)
        {
            m_param1 = getArguments().getString(ARG_PARAM1);
            m_param2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        m_view = inflater.inflate(R.layout.fragment_bluetooth_list, container, false);
        m_context = getContext();

        if(ContextCompat.checkSelfPermission(m_context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(getActivity(), new String[]{
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1);
        }
        //动态注册注册广播接收器,接收蓝牙发现讯息
        IntentFilter btFilter = new IntentFilter();
        btFilter.setPriority(1000);
        btFilter.addAction(BluetoothDevice.ACTION_FOUND);
        btFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        //获取蓝牙适配器
        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        m_checkBox = m_view.findViewById(R.id.ck_bluetooth);
        m_textView1 = m_view.findViewById(R.id.tv_discovery);
        m_listView = m_view.findViewById(R.id.lv_bluetooth);
        switch(m_bluetoothAdapter.getState())
        {
            case BluetoothAdapter.STATE_ON:
            case BluetoothAdapter.STATE_TURNING_ON:
                m_checkBox.setChecked(true);
                break;
            case BluetoothAdapter.STATE_OFF:
            case BluetoothAdapter.STATE_TURNING_OFF:
            default:
                m_checkBox.setChecked(false);
                break;
        }
        m_checkBox.setOnCheckedChangeListener(this);
        m_textView1.setOnClickListener(this);
        if(m_bluetoothAdapter == null)
        {
            Toast.makeText(m_context, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
        }
        //m_listener.onFragmentInteraction(Uri.parse(""));

        //下拉刷新控件
        m_materialRefreshLayout = m_view.findViewById(R.id.refresh);
        //启用加载更多
        m_materialRefreshLayout.setLoadMore(false);
        m_materialRefreshLayout.setMaterialRefreshListener(new MaterialRefreshListener()
        {
            /**
             * 下拉刷新
             * @param materialRefreshLayout
             */
            @Override
            public void onRefresh(final MaterialRefreshLayout materialRefreshLayout)
            {
                materialRefreshLayout.postDelayed(() ->
                {
                    m_deviceList.clear();
                    BluetoothListAdapter adapter = new BluetoothListAdapter(m_context, m_deviceList);
                    m_listView.setAdapter(adapter);
                    m_textView1.setText("正在搜索蓝牙设备");
                    //startDiscovery虽然兼容经典蓝牙和低功耗蓝牙,但有些设备无法检测到低功耗蓝牙
                    m_bluetoothAdapter.startDiscovery();
                    //结束下拉刷新
                    materialRefreshLayout.finishRefresh();
                }, 1 * 1000);
            }

            /**
             * 加载完毕
             */
            @Override
            public void onfinish()
            {
                //Toast.makeText(m_context, "完成", Toast.LENGTH_LONG).show();
            }

            /**
             * 加载更多
             * @param materialRefreshLayout
             */
            @Override
            public void onRefreshLoadMore(MaterialRefreshLayout materialRefreshLayout)
            {
                Toast.makeText(m_context, "别滑了,到底了", Toast.LENGTH_SHORT).show();
            }
        });
        //自动刷新
        m_materialRefreshLayout.autoRefresh();
        //使用线性布局
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(m_context);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        m_checkBox1 = m_view.findViewById(R.id.checkBox);
        m_radioGroup = m_view.findViewById(R.id.radioGroup);
        m_radioGroup.setOnCheckedChangeListener(this::onCheckedChanged);
        //强制获得焦点
        m_view.requestFocus();
        m_view.setFocusable(true);
        m_view.setFocusableInTouchMode(true);
        m_view.setOnKeyListener(backListener);
        return m_view;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if(context instanceof OnFragmentInteractionListener)
        {
            m_listener = (OnFragmentInteractionListener) context;
        }
        else
        {
            throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        m_listener = null;
        m_deviceList.clear();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        CancelDiscovery();
        m_context.unregisterReceiver(m_bluetoothReceiver);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        CancelDiscovery();
        m_bluetoothAdapter.disable();
        m_deviceList.clear();
        BluetoothListAdapter adapter = new BluetoothListAdapter(m_context, m_deviceList);
        m_listView.setAdapter(adapter);
    }
}
