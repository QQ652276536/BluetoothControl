package com.zistone.bluetoothcontrol;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.zistone.bluetoothcontrol.fragment.BluetoothFragment;
import com.zistone.bluetoothcontrol.fragment.BluetoothFragment_List;
import com.zistone.bluetoothcontrol.fragment.BluetoothFragment_OTA;
import com.zistone.bluetoothcontrol.fragment.BluetoothFragment_PowerControl;
import com.zistone.bluetoothcontrol.fragment.BluetoothFragment_CommandTest;

public class MainActivity extends AppCompatActivity implements BluetoothFragment.OnFragmentInteractionListener, BluetoothFragment_List.OnFragmentInteractionListener, BluetoothFragment_CommandTest.OnFragmentInteractionListener, BluetoothFragment_PowerControl.OnFragmentInteractionListener, BluetoothFragment_OTA.OnFragmentInteractionListener
{
    public static final int ACTIVITYRESULT_WRITEVALUE = 1;
    public static final int ACTIVITYRESULT_PARAMSETTING = 2;
    public static final int ACTIVITYRESULT_OTA = 3;

    public BluetoothFragment m_bluetoothFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InitData();
    }

    private void InitData()
    {
        m_bluetoothFragment = BluetoothFragment.newInstance("", "");
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_current, m_bluetoothFragment, "bluetoothFragment").show(m_bluetoothFragment).commitNow();
    }

    /**
     * Fragment向Activtiy传递数据
     *
     * @param uri
     */
    @Override
    public void onFragmentInteraction(Uri uri)
    {
        Toast.makeText(this, uri.toString(), Toast.LENGTH_LONG).show();
    }
}