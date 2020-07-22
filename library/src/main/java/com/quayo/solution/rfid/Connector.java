package com.quayo.solution.rfid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.motorolasolutions.ASCII_SDK.ASCIIUtil;
import com.motorolasolutions.ASCII_SDK.COMMAND_TYPE;
import com.motorolasolutions.ASCII_SDK.CONFIG_TYPE;
import com.motorolasolutions.ASCII_SDK.Command_Inventory;
import com.motorolasolutions.ASCII_SDK.Command_LocateTag;
import com.motorolasolutions.ASCII_SDK.Command_SetAntennaConfiguration;
import com.motorolasolutions.ASCII_SDK.Command_SetDynamicPower;
import com.motorolasolutions.ASCII_SDK.Command_SetQueryParams;
import com.motorolasolutions.ASCII_SDK.Command_SetRegulatory;
import com.motorolasolutions.ASCII_SDK.Command_SetReportConfig;
import com.motorolasolutions.ASCII_SDK.Command_SetSelectRecords;
import com.motorolasolutions.ASCII_SDK.Command_SetStartTrigger;
import com.motorolasolutions.ASCII_SDK.Command_SetStopTrigger;
import com.motorolasolutions.ASCII_SDK.Command_abort;
import com.motorolasolutions.ASCII_SDK.ENUM_TRIGGER_ID;
import com.motorolasolutions.ASCII_SDK.IMsg;
import com.motorolasolutions.ASCII_SDK.Notification;
import com.motorolasolutions.ASCII_SDK.Notification_StartOperation;
import com.motorolasolutions.ASCII_SDK.Notification_StopOperation;
import com.motorolasolutions.ASCII_SDK.Notification_TriggerEvent;
import com.motorolasolutions.ASCII_SDK.Param_AccessConfig;
import com.motorolasolutions.ASCII_SDK.RESPONSE_TYPE;
import com.motorolasolutions.ASCII_SDK.ResponseMsg;
import com.motorolasolutions.ASCII_SDK.Response_TagData;
import com.motorolasolutions.ASCII_SDK.Response_TagProximityPercent;
import com.quayo.solution.rfid.listener.ConnectionListener;
import com.quayo.solution.rfid.listener.OnProximityChangeListener;
import com.quayo.solution.rfid.listener.OnTagCountChangeListener;
import com.quayo.solution.rfid.listener.RFIDEventListener;
import com.quayo.solution.rfid.task.ResponseHandlerTask;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by firas on 12/07/2016.
 */
public abstract class Connector implements GenericReader.GenericReaderResponseParsedListener, GenericReader.ResponseTagHandler, GenericReader.TriggerEventHandler, Serializable {

    private static final String TAG = Connector.class.getName();

    private static boolean is_disconnection_requested = false;
    Activity activity;
    public BluetoothService bluetoothService;
    public static Handler mHandler;
    public GenericReader genericReader;
    boolean isOK = false;
    public static ArrayList<Response_TagData> list = new ArrayList<>();
    public static String macAddress;
    public ArrayList<BluetoothDevice> rfidDevices = new ArrayList<>();


    public static TreeMap<String, Integer> inventoryList = new TreeMap<>();
    //Variable to keep track of the unique tags seen
    public static volatile int UNIQUE_TAGS = 0;

    //variable to keep track of the total tags seen
    public static volatile int TOTAL_TAGS = 0;

    public static int inventoryMode = 0;
    //Arraylist to keeptrack of the tags read for Inventory
    public static ArrayList<InventoryListItem> tagsReadInventory = new ArrayList<>();
    private static boolean isMessageShown;


    List<InventoryListItem> inventoryItems = new ArrayList<>();
    List<String> inv = new ArrayList<>();

    private OnProximityChangeListener proximityChangeListener;
    private OnTagCountChangeListener tagCountChangeListener;
    private RFIDEventListener eventListener;
    private ConnectionListener connectionListener;

    protected abstract void ShowMessageBox(final String message, final Activity activity);
    protected abstract void handleScannedItem(String data);
    protected void dismissProgressDialog(){

    }
    public final void retryToConnect(Activity activity){
        bluetooth(activity);
    }

    public static class MOTOROLA_DW {
        // This intent string contains the source of the data as a string
        public static final String SOURCE_TAG = "com.motorolasolutions.emdk.datawedge.source";
        // This intent string contains the captured data as a string
        // (in the case of MSR this data string contains a concatenation of the
        // track data)
        public static final String DATA_STRING_TAG = "com.motorolasolutions.emdk.datawedge.data_string";

        // Let's define the API intent strings for the soft scan trigger
        public static final String ACTION_SOFTSCANTRIGGER = "com.motorolasolutions.emdk.datawedge.api.ACTION_SOFTSCANTRIGGER";
        public static final String EXTRA_PARAM = "com.motorolasolutions.emdk.datawedge.api.EXTRA_PARAMETER";
        public static final String DWAPI_TOGGLE_SCANNING = "TOGGLE_SCANNING";
        public static final String BARCODE_DETECTED_ACTION = "com.quayo.sfa.scanner";
    }

    public void resetClassData() {
        inventoryList = new TreeMap<>();
        UNIQUE_TAGS = 0;
        TOTAL_TAGS = 0;
        inventoryMode = 0;
        tagsReadInventory = new ArrayList<>();
        inv = new ArrayList<>();
        inventoryItems = new ArrayList<>();
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void initialize(Activity activity) {

        try {
            this.activity = activity;
            inventoryItems = new ArrayList<>();
            inv = new ArrayList<>();
            mHandler = initializeHandler(activity);
            bluetoothService = new BluetoothService(activity, mHandler);
            genericReader = new GenericReader();
            genericReader.attachActivity(this, bluetoothService);
            bluetoothService.setGenericReader(genericReader);
            rfidBR(activity);
//            Bluetooth(activity);
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    private void rfidBR(final Activity activity) throws Exception{
        bReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // add the name and the MAC address of the object to the arrayAdapter
                    // get paired devices
//                    if (device.getName().startsWith("RFD") && device.getName().length() == 9) {
                    if (device.getName().startsWith("RFD")) {

                        dismissProgressDialog();


//                        deviceAdapter.add(device.getName() + "\n" + device.getAddress());
                        //deviceAdapter.notifyDataSetChanged();
                    }
                }
            }
        };
        bReceiverFail = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(String.valueOf(BluetoothService.MESSAGE_CONN_FAILED).equals(action)){
                    retryToConnect(activity);
                }
            }
        };

        activity.registerReceiver(bReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        activity.registerReceiver(bReceiverFail, new IntentFilter(String.valueOf(BluetoothService.MESSAGE_CONN_FAILED)));
    }

    public void connect(Activity activity) {
        if (isOK)
            try {
                connectToBluetoothDevice(loadAvailableReaders(bluetoothService).get(0).getBluetoothDevice(), bluetoothService, mHandler, activity);
                isOK = false;
            } catch (Throwable ex) {
                ShowMessageBox(activity.getString(R.string.print_message_unknown_host_exception), activity);
                Log.e(TAG, ex.toString());
            }
    }

    public void disconnect() {
        if (loadAvailableReaders(bluetoothService).size() > 0)
            disconnectFromBluetoothDevice(loadAvailableReaders(bluetoothService).get(0).getBluetoothDevice(), bluetoothService);
//        for (int i = 0; i < list.size(); i++) {
//            String r = list.get(i).EPCId;
//            Log.d("tagggggggggggssss", r);
//        }
//        Log.d("macAddress", macAddress);
    }

    public void locate(String tagID) {
        byte[] epc = (byte[]) ASCIIUtil.ParseArrayFromString(tagID, "byteArray", "HEX");
        locateTag(genericReader, epc);
    }

    private void connectToBluetoothDevice(BluetoothDevice device, BluetoothService bluetoothService, Handler mHandler, Activity activity) {

        if (bluetoothService != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
            try {
                bluetoothService.connect(device);
                macAddress = device.getAddress();
            } catch (Exception ex) {
                Log.d("errorrr", "no device active");
                bluetooth(activity);
            }
        } else {
//            Message msg = mHandler.obtainMessage(BluetoothService.MESSAGE_CONN_FAILED);
//            Bundle bundle = new Bundle();
//            bundle.putString("TEST MLAMAA", "Unable to connect device");
//            bundle.putParcelable(Constants.DATA_BLUETOOTH_DEVICE, device);
//            msg.setData(bundle);
//            mHandler.sendMessage(msg);
        }
    }

    public static void registerRFIDDevice(String MACAddress) {

    }

    public static String readRFIDDevice() {
        return "";
    }

    public static void clearRFIDDevice(String MACAddress) {

    }

    public void setProximityChangeListener(OnProximityChangeListener proximityChangeListener) {
        this.proximityChangeListener = proximityChangeListener;
    }

    public void setTagCountChangeListener(OnTagCountChangeListener tagCountChangeListener) {
        this.tagCountChangeListener = tagCountChangeListener;
    }

    public void setRFIDEventListener(RFIDEventListener eventListener){
        this.eventListener = eventListener;
    }

    public ArrayList<ReaderDevice> loadAvailableReaders(BluetoothService bluetoothService) {
        ArrayList<ReaderDevice> Readers = new ArrayList<>();
        HashSet<BluetoothDevice> availableReaders = new HashSet<>();
        bluetoothService.getAvailableDevices(availableReaders);
        for (BluetoothDevice device : availableReaders)
            Readers.add(new ReaderDevice(device, device.getName(), device.getAddress(), null, null, false));
        return Readers;
    }

    private void disconnectFromBluetoothDevice(BluetoothDevice device, BluetoothService bluetoothService) {
        if (bluetoothService != null) {
            is_disconnection_requested = true;
            if (device != null)
                bluetoothService.disconnect(device);
        }
    }

    public static boolean isConnected(BluetoothService bluetoothService) {
        return bluetoothService.isConnected();
    }

    public void locateTag(GenericReader genericReader, byte[] epc) {
        Command_LocateTag command_locateTag = new Command_LocateTag();
        command_locateTag.setepc(epc);
        genericReader.sendCommand(command_locateTag);
    }

    public boolean isOK(){
        return isOK;
    }

    @Override
    public void responseDataParsedFromGenericReader(ResponseMsg responseMsg) {

        if (RESPONSE_TYPE.TAGDATA == responseMsg.getResponseType()) {

            //this block of code is used to read automaticly without a click
//            final  Response_TagData response_tagData = (Response_TagData) responseMsg;
//            if (activity  instanceof  Asset_Client_List_Activity) {
//                ((Asset_Client_List_Activity) activity).scanMode=Asset_Client_List_Activity.RFID_SCAN_MODE;
//           int rand=10+new Random().nextInt()/1000;
//                mHandler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        ((Asset_Client_List_Activity) activity).checkAssetCode(response_tagData.EPCId);
//                    }
//                }, rand);
//            }
            final Response_TagData response_tagData = (Response_TagData) responseMsg;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    new ResponseHandlerTask(Connector.this, response_tagData).execute();
                }
            });
        } else if (RESPONSE_TYPE.STATUS == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.SUPPORTEDLINKPROFILES == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.SUPPORTEDREGIONS == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.REGULATORYCONFIG == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.VERSIONINFO == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.TAGPROXIMITYPERCENT == responseMsg.getResponseType()) {
            if (proximityChangeListener != null)
                proximityChangeListener.onChange((Response_TagProximityPercent) responseMsg);
        } else if (RESPONSE_TYPE.CAPABILITIES == responseMsg.getResponseType()) {
        } else if (RESPONSE_TYPE.ATTRIBUTEINFO == responseMsg.getResponseType()) {
        }

    }

    @Override
    public void notificationFromGenericReader(Notification notification) {

        if (notification instanceof Notification_StartOperation) {


        } else if (notification instanceof Notification_StopOperation) {


        } else if (notification instanceof Notification_TriggerEvent) {

            Notification_TriggerEvent triggerEvent = (Notification_TriggerEvent) notification;

            if (triggerEvent.TriggerValue == ENUM_TRIGGER_ID.TRIGGER_PRESS)
                triggerPressEventRecieved();
            else if (triggerEvent.TriggerValue == ENUM_TRIGGER_ID.TRIGGER_RELEASE)
                triggerReleaseEventRecieved();

        }
    }


    public void configurationsFromReader(IMsg msg) {
        if (msg instanceof Command_SetAntennaConfiguration) {
        } else if (msg instanceof Command_SetQueryParams) {
        } else if (msg instanceof Command_SetStartTrigger) {
        } else if (msg instanceof Command_SetStopTrigger) {
        } else if (msg instanceof Command_SetReportConfig) {
        } else if (msg instanceof Command_SetRegulatory) {
        } else if (msg instanceof Command_SetSelectRecords) {
        } else if (msg instanceof Command_SetDynamicPower) {
            //dynamicPowerSettings = (Command_SetDynamicPower) msg;
        }
    }

    protected void initializeService() {
        if (bluetoothService == null)
            bluetoothService = new BluetoothService(activity, mHandler);

        if (genericReader == null) {
            genericReader = new GenericReader();
            genericReader.attachActivity(this, bluetoothService);
        }
    }

    private Handler initializeHandler(final Activity activity) {

        return new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                BluetoothDevice device;
                switch (msg.what) {
                    case BluetoothService.MESSAGE_STATE_CHANGE:
                        Constants.logAsMessage(Constants.TYPE_DEBUG, "Tag", "MESSAGE_STATE_CHANGE: " + msg.arg1);
                        switch (msg.arg1) {
                            case BluetoothService.STATE_CONNECTED:
                                Constants.logAsMessage(Constants.TYPE_DEBUG, "Tag", "STATE_CONNECTED: " + msg.arg1);
                                break;
                            case BluetoothService.STATE_CONNECTING:
                                Constants.logAsMessage(Constants.TYPE_DEBUG, "Tag", "STATE_CONNECTING: " + msg.arg1);
                                break;
                            case BluetoothService.STATE_LISTEN:
                                Constants.logAsMessage(Constants.TYPE_DEBUG, "Tag", "STATE_LISTEN: " + msg.arg1);
                            case BluetoothService.STATE_NONE:
                                Constants.logAsMessage(Constants.TYPE_DEBUG, "Tag", "STATE_NONE: " + msg.arg1);
                                break;

                        }
                        break;
                    case BluetoothService.MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        device = (BluetoothDevice) msg.getData().getParcelable(Constants.DATA_BLUETOOTH_DEVICE);
                        ReaderDevice readerDevice = new ReaderDevice(device, device.getName(), device.getAddress(), null, null, true);
//                            Application.mConnectedDevice = readerDevice;
                        break;
                    case BluetoothService.MESSAGE_TOAST:
                        Toast.makeText(activity, msg.getData().getString("toast"),
                                Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothService.MESSAGE_CONN_FAILED:
                        //TO handle reconnection.
                        //Toast.makeText(getApplicationContext(), "Connection Failed!! was received", Toast.LENGTH_SHORT).show();
                        BluetoothDevice bluetoothDevice = (BluetoothDevice) msg.getData().getParcelable(Constants.DATA_BLUETOOTH_DEVICE);
                        Intent broadcastIntent = new Intent();
                        broadcastIntent.setAction(String.valueOf(BluetoothService.MESSAGE_CONN_FAILED));
                        activity.sendBroadcast(broadcastIntent);
                        break;

                }
            }
        };

    }

    public void bluetooth(Activity activity) {
        BluetoothAdapter btAdapter;
        try {
            //Get BlueTooth Adapter
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null) {
                ShowMessageBox(activity.getString(R.string.print_message_bluetooth_not_supported), activity);
                return;
            } else if (!btAdapter.isEnabled()) {
                ShowMessageBox(activity.getString(R.string.print_message_turn_on_bluetooth), activity);
                return;
            }

            //get the list of paired printer.
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            for (BluetoothDevice rfd : pairedDevices)
                if (bluetoothService.isRFIDReader(rfd))
                    rfidDevices.add(rfd);
            //if there is any paired device, get out
            if (rfidDevices.size() == 0) {
                ShowMessageBox(activity.getResources().getString(R.string.message_no_paired_rfid_devices), activity);
                return;
            }

            if (btAdapter.isEnabled() && rfidDevices.size() != 0) {
                isOK = true;
                if(connectionListener != null)
                    connectionListener.connect();
                //     ((Tabs_Activity)activity).showRfidProgressDialog();
                connect(activity);
            }

        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    public void abort() {
        genericReader.sendCommand(new Command_abort());
    }

    //these methods to add the readed tags to a list and get number of tags readed as total
    @Override
    public void handleTagResponse(InventoryListItem inventoryListItem, boolean isAddedToList) {
        if (tagCountChangeListener != null)
            tagCountChangeListener.onChange(UNIQUE_TAGS);
        if (isAddedToList)
            inventoryItems.add(inventoryListItem);
    }

    //these method to handel the button click trigger of the rfid device
    @Override
    public void triggerPressEventRecieved() {
        readTags(genericReader);
        if (eventListener != null)
            eventListener.triggerPressEvent();
    }

    @Override
    public void triggerReleaseEventRecieved() {
        abort();
        for (final InventoryListItem tag : inventoryItems)
            inv.add(tag.getTagID());
        if (eventListener != null)
            eventListener.triggerReleaseEvent(inv);
    }

    public static String asciiToHex(String asciiValue) {
        char[] chars = asciiValue.toCharArray();
        StringBuffer hex = new StringBuffer();
        for (int i = 0; i < chars.length; i++) {
            hex.append(Integer.toHexString((int) chars[i]));
        }
        return hex.toString();
    }

    public static String hexToASCII(String hexValue) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexValue.length(); i += 2) {
            String str = hexValue.substring(i, i + 2);
            if (!str.equals("00"))
                output.append((char) Integer.parseInt(str, 16));

        }
        return output.toString();
    }

    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    private  void unregisterBReceiver(Activity activity){

        if(bReceiver != null){
            activity.unregisterReceiver(bReceiver);
            bReceiver =null;
        }
    }
    private  void unregisterBReceiverFail(Activity activity){

        if(bReceiverFail != null){
            activity.unregisterReceiver(bReceiverFail);
            bReceiverFail =null;
        }
    }

    public void onDestroy(Activity activity){
        unregisterBReceiver(activity);
        unregisterBReceiverFail(activity);
    }

    public void setUpScannerReceiverRFID(Activity activity) {
        releaseMotorolaScanner(activity);
        isScannerReady = true;
        if(myReceiver==null){
            myReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent i) {
                    if (isScannerReady )
                    {
                        isScannerReady = false;
                        // get the source of the data
                        String source = i.getStringExtra(MOTOROLA_DW.SOURCE_TAG);
                        // save it to use later
                        if (source == null)
                            source = "scanner";
                        // get the data from the intent
                        String data = i.getStringExtra(MOTOROLA_DW.DATA_STRING_TAG);

                        if (data != null && data.length() > 0)
                            handleScannedItem(data);
                    }
                }
            };
            intentFilter.addAction(MOTOROLA_DW.BARCODE_DETECTED_ACTION);
        }
        if(!myReceiverRegistered){
            myReceiverRegistered = true;
            activity.registerReceiver(myReceiver, intentFilter);
            isScannerReady = true;
        }
    }

    private void releaseMotorolaScanner(Activity activity){
        if(myReceiver != null){
            activity.unregisterReceiver(myReceiver);
            myReceiver =null;
            myReceiverRegistered = false;
        }
    }

    private void readTags(final GenericReader genericReader) {
        /* the two commands setRegulatory and setDynamicPower are needed to allow the reader to read  **/
        genericReader.sendCommand(applyCommand);
        genericReader.sendCommand(COMMAND_TYPE.COMMAND_SETREGULATORY, CONFIG_TYPE.CURRENT);

        dynamicPowerSettings.setDisable(true);
        genericReader.sendCommand(dynamicPowerSettings);
        genericReader.sendCommand(COMMAND_TYPE.COMMAND_SETDYNAMICPOWER, CONFIG_TYPE.CURRENT);

        Command_Inventory inventoryCommand = new Command_Inventory();
        Param_AccessConfig accessConfig = new Param_AccessConfig();
        accessConfig.setDoSelect(true);
        inventoryCommand.AccessConfig = accessConfig;
        genericReader.sendCommand(inventoryCommand);
    }

    public static Command_SetRegulatory applyCommand = new Command_SetRegulatory();
    //bynamic power
    public static Command_SetDynamicPower dynamicPowerSettings = new Command_SetDynamicPower();
    BroadcastReceiver myReceiver;
    IntentFilter intentFilter = new IntentFilter();
    private boolean isScannerReady;
    private boolean myReceiverRegistered = false;
    private BroadcastReceiver bReceiver = null;
    private BroadcastReceiver bReceiverFail = null;
}