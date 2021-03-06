package testcom.carranzafp.btapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by Dave Smith
 * Double Encore, Inc.
 * MainActivity
 */

//MRC: download icons caribean blue from here //http://www.iconsdb.com/caribbean-blue-icons/coins-icon.html

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "BluetoothGattActivity";

    private static final String DEVICE_NAME = "SensorTag";

    /* Battery Service */
    //"00000000-0000-1000-8000-00805f9b34fb" //BASE UUID must be hex in lowercase !!
    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_DATA_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");


    /* Counter (private service */
    private static final UUID COUNTER_SERVICE = UUID.fromString("11223344-5566-7788-9900-aabbccddeeff");
    private static final UUID COUNTER_DATA_CHAR = UUID.fromString("01020304-0506-0708-0900-0a0b0c0d0e0f");  //This service must be readable, notifiable and must have 4 bytes length
    /* Dummy Write Characteristic (private service)*/
    private static final UUID DUMMYWRITE_DATA_CHAR = UUID.fromString("11121314-1516-1718-1910-1a1b1c1d1e1f");  //This service must be writeable, notifiable and must have 2 bytes length

    /* Humidity Service */
    private static final UUID HUMIDITY_SERVICE = UUID.fromString("f000aa20-0451-4000-b000-000000000000");
    private static final UUID HUMIDITY_DATA_CHAR = UUID.fromString("f000aa21-0451-4000-b000-000000000000");
    private static final UUID HUMIDITY_CONFIG_CHAR = UUID.fromString("f000aa22-0451-4000-b000-000000000000");
    /* Barometric Pressure Service */
    private static final UUID PRESSURE_SERVICE = UUID.fromString("f000aa40-0451-4000-b000-000000000000");
    private static final UUID PRESSURE_DATA_CHAR = UUID.fromString("f000aa41-0451-4000-b000-000000000000");
    private static final UUID PRESSURE_CONFIG_CHAR = UUID.fromString("f000aa42-0451-4000-b000-000000000000");
    private static final UUID PRESSURE_CAL_CHAR = UUID.fromString("f000aa43-0451-4000-b000-000000000000");
    /* Client Configuration Descriptor */
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;

    private TextView mTemperature, mCounter, mBattery;
    //, mPressure;
    private Button mSendCmd;
    //private EditText mCommand;

    private ProgressDialog mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);
        setProgressBarIndeterminate(true);

        /*
         * We are going to display the results in some text fields
         */
        mTemperature = (TextView) findViewById(R.id.text_temperature);
        mCounter = (TextView) findViewById(R.id.text_counter);
        //mCommand = (EditText) findViewById(R.id.text_command);
        mSendCmd = (Button)findViewById(R.id.button_sendcmd);
        mBattery = (TextView) findViewById(R.id.text_battery);
        //mPressure=(TextView)findViewById(R.id.text_pressure);

        mSendCmd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //invoke write data here
                BluetoothGattCharacteristic characteristic;
                characteristic=mConnectedGatt.getService(COUNTER_SERVICE).getCharacteristic(DUMMYWRITE_DATA_CHAR);
                characteristic.setValue(new byte[] {0x02,0x01});    //Send dummy data
                mConnectedGatt.writeCharacteristic(characteristic);

/*                //Code to unpair bonded devices
                for(BluetoothDevice bonded : mBluetoothAdapter.getBondedDevices()) {
                    Log.d(TAG, "Removing Device Found: " + bonded.getName());
                    try {
                        Method m = bonded.getClass().getMethod("removeBond", (Class[]) null);
                        m.invoke(bonded, (Object[]) null);
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                }*/


            }
        });





        /*
         * Bluetooth in Android 4.3 is accessed via the BluetoothManager, rather than
         * the old static BluetoothAdapter.getInstance()
         */
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();


        //Now add the bonded devices
        for(BluetoothDevice bonded : mBluetoothAdapter.getBondedDevices()) {
            Log.d(TAG,"Bonded Device Found: " + bonded.getName());
            mDevices.put(bonded.hashCode(), bonded);

        }


        /*
         * A progress dialog will be needed while the connection process is
         * taking place
         */
        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Make sure dialog is hidden
        mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Disconnect from any active tag connection
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int titems;
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);

        //Add any device elements we've discovered to the overflow menu
        titems=0;
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
            titems++;
        }

        if(titems==0) {
            Toast.makeText(this,"Press Scan to detect devices",Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this,"Select Device from the List to connect",Toast.LENGTH_SHORT).show();
        }


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                if(mDevices.size()>0) {
                    //Obtain the discovered device to connect with
                    BluetoothDevice device = mDevices.get(item.getItemId());
                    Log.i(TAG, "Connecting to " + device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                    mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                    //Display progress UI
                    mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));
                    return super.onOptionsItemSelected(item);
                }
                else {
                    //Saving from a crash hopefully
                    Log.w(TAG, "No devices detected");
                    return false;
                }
        }
    }

    private void clearDisplayValues() {
        mBattery.setText("---");
        mTemperature.setText("---");
        mCounter.setText("-----");
        //mPressure.setText("---");
    }


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        mBluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500);
    }

    private void stopScan() {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    /* BluetoothAdapter.LeScanCallback */

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        /*
         * We are looking for SensorTag devices only, so validate the name
         * that each device reports before adding it to our collection
         */
//        if (DEVICE_NAME.equals(device.getName())) {
            mDevices.put(device.hashCode(), device);
            //Update the overflow menu
            invalidateOptionsMenu();
//        }
    }

    /*
     * In this callback, we've created a bit of a state machine to enforce that only
     * one characteristic be read or written at a time until all of our sensors
     * are enabled and we are registered to get notifications.
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;

        private void reset() { mState = 0;
            Log.d(TAG, "State machine Reset()");
        }

        private void advance() { mState++;
            Log.d(TAG, "State machine newstate:"+mState);
        }

        /*
         * Send an enable command to each sensor by writing a configuration
         * characteristic.  This is specific to the SensorTag to keep power
         * low by disabling sensors you aren't using.
         */
        private void enableNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Enabling pressure cal");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x02});
                    break;
                case 1:
                    Log.d(TAG, "Enabling pressure");
                    characteristic = gatt.getService(PRESSURE_SERVICE)
                            .getCharacteristic(PRESSURE_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x01});
                    break;
                case 2:
                    Log.d(TAG, "Enabling humidity");
                    characteristic = gatt.getService(HUMIDITY_SERVICE)
                            .getCharacteristic(HUMIDITY_CONFIG_CHAR);
                    characteristic.setValue(new byte[] {0x01});
                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.writeCharacteristic(characteristic);
        }

        /*
         * Read the data characteristic's value for each sensor explicitly
         */
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Reading Battery Level");
                    characteristic = gatt.getService(BATTERY_SERVICE)
                            .getCharacteristic(BATTERY_DATA_CHAR);
                    break;
                case 1:
                    Log.d(TAG, "Reading Counter");
                    characteristic = gatt.getService(COUNTER_SERVICE)
                            .getCharacteristic(COUNTER_DATA_CHAR);
                    break;
//                case 0:
//                    Log.d(TAG, "Reading pressure cal");
//                    characteristic = gatt.getService(PRESSURE_SERVICE)
//                            .getCharacteristic(PRESSURE_CAL_CHAR);
//                    break;
//                case 1:
//                    Log.d(TAG, "Reading pressure");
//                    characteristic = gatt.getService(PRESSURE_SERVICE)
//                            .getCharacteristic(PRESSURE_DATA_CHAR);
//                    break;
//                case 2:
//                    Log.d(TAG, "Reading humidity");
//                    characteristic = gatt.getService(HUMIDITY_SERVICE)
//                            .getCharacteristic(HUMIDITY_DATA_CHAR);
//                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.readCharacteristic(characteristic);
        }

        /*
         * Enable notification of changes on the data characteristic for each sensor
         * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
         * configuration descriptor.
         */
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Set notify battery level");
                    characteristic = gatt.getService(BATTERY_SERVICE)
                            .getCharacteristic(BATTERY_DATA_CHAR);

                    break;
                case 1:
                    Log.d(TAG, "Set notify counter");
                    characteristic = gatt.getService(COUNTER_SERVICE)
                            .getCharacteristic(COUNTER_DATA_CHAR);

                    break;
//                case 0:
//                    Log.d(TAG, "Set notify pressure cal");
//                    characteristic = gatt.getService(PRESSURE_SERVICE)
//                            .getCharacteristic(PRESSURE_CAL_CHAR);
//                    break;
//                case 1:
//                    Log.d(TAG, "Set notify pressure");
//                    characteristic = gatt.getService(PRESSURE_SERVICE)
//                            .getCharacteristic(PRESSURE_DATA_CHAR);
//                    break;
//                case 2:
//                    Log.d(TAG, "Set notify humidity");
//                    characteristic = gatt.getService(HUMIDITY_SERVICE)
//                            .getCharacteristic(HUMIDITY_DATA_CHAR);
//                    break;
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            //Enable local notifications
            gatt.setCharacteristicNotification(characteristic, true);
            //Enabled remote notifications
            BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }



        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
                mSendCmd.setEnabled(true);
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                mHandler.sendEmptyMessage(MSG_CLEAR);
                mSendCmd.setEnabled(false);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
                mSendCmd.setEnabled(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered: "+status);
            mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));

            //Log the services discovered for debugging
            List<BluetoothGattService> services=gatt.getServices();
            for(BluetoothGattService service : services) {
                Log.d(TAG, " S:"+service.getUuid().toString());
                for(BluetoothGattCharacteristic chara : service.getCharacteristics()) {
                    Log.d(TAG, "  C:"+chara.getUuid().toString());
                    for(BluetoothGattDescriptor desc : chara.getDescriptors()) {
                        Log.d(TAG, "   D:"+desc.getUuid().toString());
                    }
                }
            }

            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            reset();
            //On our test all sensors are already enabled so skip this
            //enableNextSensor(gatt);
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            if (BATTERY_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_BATTERY, characteristic));
            }
            if (COUNTER_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_COUNTER, characteristic));
            }
            if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
            }
            if (PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE_CAL, characteristic));
            }

            //After reading the initial value, next we enable notifications;
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //After writing the enable flag, next we read the initial value
            Log.d(TAG, "OnCharacteristicWrite() invoked");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */
            if (BATTERY_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_BATTERY, characteristic));
            }
            if (COUNTER_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_COUNTER, characteristic));
            }
            if (PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE, characteristic));
            }
            if (PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_PRESSURE_CAL, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Once notifications are enabled, we move to the next sensor and start over with enable
            //advance();
            //enableNextSensor(gatt);
            Log.d(TAG,"onDescriptorWrite() invoked");
            advance();
            readNextSensor(gatt);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    /*
     * We have a Handler to process event results on the main thread
     */
    private static final int MSG_COUNTER = 101;
    private static final int MSG_PRESSURE = 102;
    private static final int MSG_PRESSURE_CAL = 103;
    private static final int MSG_BATTERY = 104;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_BATTERY:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining Battery Level value");
                        return;
                    }
                    updateBatteryValues(characteristic);
                    break;
                case MSG_COUNTER:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining counter value");
                        return;
                    }
                    updateCounterValues(characteristic);
                    break;
//                case MSG_PRESSURE:
//                    characteristic = (BluetoothGattCharacteristic) msg.obj;
//                    if (characteristic.getValue() == null) {
//                        Log.w(TAG, "Error obtaining pressure value");
//                        return;
//                    }
//                    updatePressureValue(characteristic);
//                    break;
                case MSG_PRESSURE_CAL:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining cal value");
                        return;
                    }
                    updatePressureCals(characteristic);
                    break;
                case MSG_PROGRESS:
                    if(!isFinishing()) { //Used to save an exception
                        mProgress.setMessage((String) msg.obj);
                        if (!mProgress.isShowing()) {
                            //try {
                                mProgress.show();
                            //}
                            //catch (Exception e) {
                            //    Log.e(TAG,"Catching Exception:",e);
                            //}
                        }
                    }
                    else {
                        Log.d(TAG,"Preventing Exception");
                    }
                    break;
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };

    /* Methods to extract sensor data and update the UI */

    private void updateBatteryValues(BluetoothGattCharacteristic characteristic) {
        double battery = SensorTagData.extractBatteryLevel(characteristic);

        mBattery.setText(String.format("%.0f%%", battery));
    }

    private void updateCounterValues(BluetoothGattCharacteristic characteristic) {
        int counter = SensorTagData.extractCounter(characteristic);
        //Log raw data of the characteristic
        //Log.d(TAG,"Counter:"+ Arrays.toString(characteristic.getValue()));

        mCounter.setText(String.format("%06d", counter));
    }

    private int[] mPressureCals;
    private void updatePressureCals(BluetoothGattCharacteristic characteristic) {
        mPressureCals = SensorTagData.extractCalibrationCoefficients(characteristic);
    }

//    private void updatePressureValue(BluetoothGattCharacteristic characteristic) {
//        if (mPressureCals == null) return;
//        double pressure = SensorTagData.extractBarometer(characteristic, mPressureCals);
//        double temp = SensorTagData.extractBarTemperature(characteristic, mPressureCals);
//
//        mTemperature.setText(String.format("%.1f\u00B0C", temp));
//        mPressure.setText(String.format("%.2f", pressure));
//    }
}
