package com.zistone.bluetoothcontrol.dialogfragment;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.zistone.bluetoothcontrol.R;
import com.zistone.bluetoothcontrol.util.DeviceFilterShared;

import java.util.ArrayList;
import java.util.List;

public class DialogFragment_DeviceFilter extends DialogFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener
{
    private static final String TAG = "DialogFragment_DeviceFilter";
    private static final String ARG_PARAM1 = "param1";
    private static Listener m_listener;
    private View m_view;
    private Context m_context;
    private Button m_button1, m_button2, m_button3, m_button4;
    private EditText m_edit1;
    private TableLayout m_table;
    private CheckBox m_chk1;

    public interface Listener
    {
        /**
         * 只显示与设置的设备名称相同的设备
         *
         * @param list 设备名
         */
        void OnlyShowSetDeviceListener(List<String> list);

        /**
         * 隐藏已连接成功的设备
         *
         * @param flag 是否开启
         */
        void HideConnectedDeviceListener(boolean flag);
    }

    public static DialogFragment_DeviceFilter newInstance(Listener listener, String str)
    {
        DialogFragment_DeviceFilter fragment = new DialogFragment_DeviceFilter();
        m_listener = listener;
        Bundle args = new Bundle();
        args.putSerializable(ARG_PARAM1, str);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onClick(View v)
    {
        switch(v.getId())
        {
            case R.id.btn2_deviceFilter:
            {
                TableRow row = new TableRow(m_context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView textView1 = new TextView(m_context);
                textView1.setText("设备名:");
                textView1.setVisibility(View.INVISIBLE);
                EditText editText = new EditText(m_context);
                editText.setEms(7);
                editText.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                ImageButton imageButton = new ImageButton(m_context);
                imageButton.setImageDrawable(getResources().getDrawable(R.drawable.close1));
                imageButton.getBackground().setAlpha(0);
                imageButton.setOnClickListener(v1 ->
                {
                    TableRow tableRow = (TableRow) v1.getParent();
                    m_table.removeView(tableRow);
                });
                row.addView(textView1);
                row.addView(editText);
                row.addView(imageButton);
                m_table.addView(row, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.MATCH_PARENT));
                break;
            }
            case R.id.btn3_deviceFilter:
            {
                List<String> list = new ArrayList<>();
                for(int i = 0; i < m_table.getChildCount(); i++)
                {
                    TableRow row = (TableRow) m_table.getChildAt(i);
                    EditText editText = (EditText) row.getChildAt(1);
                    String str = editText.getText().toString();
                    if(!str.trim().equals(""))
                    {
                        list.add(str);
                    }
                }
                DeviceFilterShared.SetFilterName(m_context, list);
                m_listener.OnlyShowSetDeviceListener(list);
                dismiss();
                break;
            }
            case R.id.btn4_deviceFilter:
            {
                dismiss();
                break;
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        m_chk1 = m_view.findViewById(R.id.chk1_deviceFilter);
        boolean flag = DeviceFilterShared.GetFilterDevice(m_context);
        if(flag)
        {
            m_chk1.setChecked(true);
        }
        else
        {
            m_chk1.setChecked(false);
        }
        m_chk1.setOnCheckedChangeListener(this::onCheckedChanged);
        m_button2 = m_view.findViewById(R.id.btn2_deviceFilter);
        m_button2.setOnClickListener(this::onClick);
        m_button3 = m_view.findViewById(R.id.btn3_deviceFilter);
        m_button3.setOnClickListener(this::onClick);
        m_button4 = m_view.findViewById(R.id.btn4_deviceFilter);
        m_button4.setOnClickListener(this::onClick);
        m_edit1 = m_view.findViewById(R.id.editText1_deviceFilter);
        m_table = m_view.findViewById(R.id.table_deviceFilter);
        List<String> list = DeviceFilterShared.GetFilterName(m_context);
        if(list != null && list.size() > 0)
        {
            String[] strArray = new String[list.size()];
            list.toArray(strArray);
            for(int i = 0; i < strArray.length; i++)
            {
                if(i == 0)
                {
                    m_edit1.setText(strArray[i]);
                }
                else
                {
                    TableRow row = new TableRow(m_context);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView textView1 = new TextView(m_context);
                    textView1.setText("设备名:");
                    EditText editText = new EditText(m_context);
                    editText.setEms(7);
                    editText.setInputType(InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
                    editText.setText(strArray[i]);
                    ImageButton imageButton = new ImageButton(m_context);
                    imageButton.setImageDrawable(getResources().getDrawable(R.drawable.close1));
                    imageButton.getBackground().setAlpha(0);
                    imageButton.setOnClickListener(v1 ->
                    {
                        TableRow tableRow = (TableRow) v1.getParent();
                        m_table.removeView(tableRow);
                    });
                    row.addView(textView1);
                    row.addView(editText);
                    row.addView(imageButton);
                    m_table.addView(row, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.MATCH_PARENT));
                }
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        m_view = LayoutInflater.from(getActivity()).inflate(R.layout.dialogfragment_devicefilter, null);
        m_context = getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(m_view);
        return builder.create();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        switch(buttonView.getId())
        {
            case R.id.chk1_deviceFilter:
                boolean flag = DeviceFilterShared.SetFilterDevie(m_context, isChecked);
                if(flag)
                {
                    Toast.makeText(m_context, "保存成功", Toast.LENGTH_SHORT).show();
                }
                m_listener.HideConnectedDeviceListener(isChecked);
                break;
        }
    }

}
