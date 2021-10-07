package com.gau.blekeyboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    protected static final String TAG = MainActivity.class.getSimpleName();
    public static BluetoothGatt mBluetoothGatt = null;
    private BluetoothLeService mBluetoothLeService;

    private boolean dataRecvFlag = false;
    private String mDeviceName = "";
    private String mDeviceAddress = "";

    /**
     * connection state
     */
    private boolean mConnected = false;
    /**
     * scan all Service ?
     */
    private boolean isInitialize = false;
    private static final int MAX_DATA_SIZE = 40 * 6;
    // / public
    private TextView textDeviceName;
    private TextView textDeviceAddress;
    private TextView textConnectionStatus;

    // / qpp start
    protected static String uuidQppService = "0000fee9-0000-1000-8000-00805f9b34fb";
    protected static String uuidQppCharWrite = "d44bc439-abfd-45a2-b575-925416129600";
//    public static byte[] uuidQppCharWrite = new byte[]{0x00, (byte) 0x96, 0x12, 0x16, 0x54, (byte) 0x92, 0x75, (byte) 0xB5,
//            (byte) 0xA2, 0x45, (byte) 0xFD, (byte) 0xAB, 0x39, (byte) 0xC4, 0x4B, (byte) 0xD4};
//
//    public static byte[] uuidQppService = new byte[]{(byte) 0xFB, 0x34, (byte) 0x9B, 0x5F, (byte) 0x80, 0x00, 0x00, (byte) 0x80,
//            0x00, 0x10, 0x00, 0x00, (byte) 0xE9, (byte) 0xFE, 0x00, 0x00};

    // / receive data
    private TextView textQppNotify;
    private TextView textQppDataRate;

    private long qppSumDataReceived = 0; // / summary of data received.
    private long qppRecvDataTime = 0;
    // / send
    private EditText editSend;
    private Button btnQppTextSend;

    // / repeat start
    private CheckBox checkboxRepeat;
    private boolean qppSendDataState = false;
    private boolean qppSendRepeat = false;
    private TextView textRepeatCounter;

    private long qppRepeatCounter = 0;
    // / repeat end

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_STATUS);
        return intentFilter;
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // Log.d(TAG,"mGattUpdateReceiver action:"+action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                setConnectState(R.string.connected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                clearHandler(handlerQppDataRate, runnableQppDataRate);
                dataRecvFlag = false;
                if (qppSendDataState) {
                    setBtnSendState("Send");
                    qppSendDataState = false;
                }
                setConnectState(R.string.disconnected);
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                final int defaultVal = 0xff;
                int status = intent.getIntExtra(action, defaultVal);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mBluetoothGatt = mBluetoothLeService.getBluetoothGatt();
                    if (QppApi.qppEnable(mBluetoothGatt, uuidQppService, uuidQppCharWrite)) {
                        isInitialize = true;
                        setConnectState(R.string.qpp_service_ready);
                    } else {
                        isInitialize = false;
                        setConnectState(R.string.no_qpp_service);
                    }
                } else {
                    isInitialize = false;
                    setConnectState(R.string.no_qpp_service);
                }
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_WRITE_STATUS.equals(action)) {
                final int defaultVal = 0xff;
                int status = intent.getIntExtra(action, defaultVal);
                if (status != 0) {
                    Log.e(TAG, "write characteristic failed");
                }
            }
        }
    };

    public static void PrintBytes(byte[] bytes) {
        if (bytes == null)
            return;
        final StringBuilder stringBuilder = new StringBuilder(bytes.length);
        for (byte byteChar : bytes)
            stringBuilder.append(String.format("%02X ", byteChar));
        Log.i(TAG, " :" + stringBuilder.toString());
    }

    void sendData() {
        Thread sendDataThread = new Thread(runnableSend);
        sendDataThread.start();
    }

    final Runnable runnableSend = new Runnable() {
        byte[] getPackageData() {
            String strInput;

            strInput = editSend.getText().toString();
            if (strInput.length() == 0)
                return null;
            if (strInput.length() % 2 == 1) {
                strInput = "0" + strInput;
            }
            return HexBytesUtils.hexStr2Bytes(strInput);
        }

        private boolean QppSendNextData() {
            byte[] data = getPackageData();
            if (data == null) {
                Log.e(TAG, "data is empty");
                return false;
            }
            int length = data.length;
            int count = 0;
            int offset = 0;
            while (offset < length) {
                if ((length - offset) < QppApi.qppServerBufferSize)
                    count = length - offset;
                else
                    count = QppApi.qppServerBufferSize;
                byte tempArray[] = new byte[count];
                System.arraycopy(data, offset, tempArray, 0, count);
                // PrintBytes(data);
                QppApi.qppSendData(mBluetoothGatt, tempArray);
                offset = offset + count;
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return true;
        }

        public void run() {
            while (qppSendDataState) {
                if (QppSendNextData()) {
                    qppRepeatCounter++;
                    setRepeatCounter(" " + qppRepeatCounter);
                } else {
                    // add error counter?
                }
                if (!qppSendRepeat) {
                    qppSendDataState = false;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    };

    private void clearHandler(Handler handler, Runnable runner) {
        if (handler != null) {
            handler.removeCallbacks(runner);
            handler = null;
        }
    }

    final Handler handlerQppDataRate = new Handler();
    final Runnable runnableQppDataRate = new Runnable() {
        public void run() {
            qppRecvDataTime++;
            textQppDataRate.setText(" " + qppSumDataReceived / qppRecvDataTime + " Bps");

            dataRecvFlag = false;
        }
    };

    private void setConnectState(final int stat) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textConnectionStatus.setText(stat);
            }
        });
    }

    private void setQppNotify(final String errStr) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textQppNotify.setText(errStr);
            }
        });
    }

    private void setRepeatCounter(final String errStr) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textRepeatCounter.setText(errStr);
            }
        });
    }

    private void setBtnSendState(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnQppTextSend.setText(str);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qpp);
//        getActionBar().setTitle(R.string.title_version);

        textDeviceName = (TextView) findViewById(R.id.text_device_name);
        textDeviceAddress = (TextView) findViewById(R.id.text_device_address);
        textConnectionStatus = (TextView) findViewById(R.id.text_connection_state);
        editSend = (EditText) findViewById(R.id.edit_send);
        editSend.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_DATA_SIZE)});
        btnQppTextSend = (Button) findViewById(R.id.btn_qpp_text_send);
        checkboxRepeat = (CheckBox) findViewById(R.id.cb_repeat);
        textRepeatCounter = (TextView) findViewById(R.id.text_repeat_counter);
        textQppNotify = (TextView) findViewById(R.id.text_qpp_notify);
        textQppDataRate = (TextView) findViewById(R.id.text_qpp_data_rate);

        textDeviceName.setText(mDeviceName);
        textDeviceAddress.setText(mDeviceAddress);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        QppApi.setCallback(new iQppCallback() {

            @Override
            public void onQppReceiveData(BluetoothGatt mBluetoothGatt, String qppUUIDForNotifyChar, byte[] qppData) {
                if (!dataRecvFlag) {
                    handlerQppDataRate.postDelayed(runnableQppDataRate, 1000);
                    dataRecvFlag = true;
                }
                qppSumDataReceived = qppSumDataReceived + qppData.length;
                setQppNotify("0x" + HexBytesUtils.bytes2HexStr(qppData));
            }

        });
        /**
         * start to send qpp package OR RESET...
         */
        btnQppTextSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mConnected || !isInitialize) {
                    Toast.makeText(MainActivity.this,
                            "Please connect device first and ensure your device support Qpp service!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                qppSendRepeat = checkboxRepeat.isChecked();
                if (qppSendRepeat) {
                    if (!qppSendDataState) {
                        qppRepeatCounter = 0;
                        qppSendDataState = true;
                        btnQppTextSend.setText("Stop");
                        // handlersend.post(runnableSend);
                        sendData();
                    } else {
                        qppSendDataState = false;
                        btnQppTextSend.setText("Send");
                    }
                } else {
                    btnQppTextSend.setText("Send");
                    qppRepeatCounter = 0;
                    qppSendDataState = true;
                    // handlersend.post(runnableSend);
                    sendData();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.qpp, menu);

        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    if (device.getName().equals("BLE-KeyBoard")) {
                        mDeviceName = device.getName();
                        mDeviceAddress = device.getAddress();
                    }
                }
                textDeviceName.setText(mDeviceName);
                textDeviceAddress.setText(mDeviceAddress);

                return true;
            case R.id.menu_connect:
                if (!mConnected)
                    mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                if (mConnected)
                    mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            if (!mBluetoothLeService.getConnectionState()) {
                mConnected = false;
                invalidateOptionsMenu();
                mBluetoothLeService.connect(mDeviceAddress);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearHandler(handlerQppDataRate, runnableQppDataRate);
        mBluetoothLeService.close();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }
}