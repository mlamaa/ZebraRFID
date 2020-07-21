package com.quayo.solution.rfid;

import android.util.Log;

import com.motorolasolutions.ASCII_SDK.ASCIIProcessor;
import com.motorolasolutions.ASCII_SDK.COMMAND_TYPE;
import com.motorolasolutions.ASCII_SDK.CONFIG_TYPE;
import com.motorolasolutions.ASCII_SDK.Command;
import com.motorolasolutions.ASCII_SDK.IMsg;
import com.motorolasolutions.ASCII_SDK.MetaData;
import com.motorolasolutions.ASCII_SDK.Notification;
import com.motorolasolutions.ASCII_SDK.ResponseMsg;

/**
 * Class used to communicate with underlying bluetooth layers. It also handles the parsing of data fetched from the Bluetooth layer
 */
public class GenericReader implements BluetoothService.BluetoothDataHandler {
    MetaData metaData;
    //Service used for communicating with the bluetooth
    BluetoothService bluetoothService;
    //Activity which implements GenericReaderResponseParsedListener to handle the Generic Reader events
    public GenericReaderResponseParsedListener genericListener;

    public GenericReader() {
        metaData = null;
    }

    /**

     * @param genericListener - Activity which wants to be notified
     */
    public void attachActivity(GenericReaderResponseParsedListener genericListener, BluetoothService service) {
        this.genericListener = genericListener;
        this.bluetoothService = service;
    }

    /**
     * Method which registers an instance of {@link GenericReaderResponseParsedListener} to which data should be sent after parsing
     *
     * @param genericListener - Listener to be registered
     */
    public void setGenericListener(GenericReaderResponseParsedListener genericListener) {
        this.genericListener = genericListener;
    }

    /**
     * Method to send a command to the underlying bluetooth layers
     *
     * @param cmd - Command to be sent
     */
    public void sendCommand(Command cmd) {
        String cmdOut = ASCIIProcessor.getCommandString(cmd);
        Constants.logAsMessage(Constants.TYPE_DEBUG, "Write data to bluetoothservice", cmdOut);
        bluetoothService.write(cmdOut.getBytes());
    }

    /**
     * Method to send a string command to the underlying bluetooth layers
     *
     * @param cmd - String to be sent
     */
    public void sendCommand(String cmd) {
        Constants.logAsMessage(Constants.TYPE_DEBUG, "Write data to bluetoothservice", cmd);
        bluetoothService.write((cmd + "\n").getBytes());
    }

    /**
     * Method to send a string command to the underlying bluetooth layers
     *
     * @param command_type -
     * @param config_type  -
     */
    public void sendCommand(COMMAND_TYPE command_type, CONFIG_TYPE config_type) {
        String cmdOut = ASCIIProcessor.getCommandConfig(command_type, config_type);
        Constants.logAsMessage(Constants.TYPE_DEBUG, "Write data to bluetoothservice", cmdOut);
        bluetoothService.write(cmdOut.getBytes());
    }

    /**
     * Method to parse the data from bluetooth
     *
     * @param msg - Data obtained from the reader
     */
    public void getReplyDetails(IMsg msg) {
        if (null != msg) {
            switch (msg.getMsgType()) {
                case COMMAND:
                    if (genericListener != null) {
                        genericListener.configurationsFromReader(msg);
                    }
                    break;
                case METADATA:
                    metaData = (MetaData) msg;
                    break;
                case RESPONSE_MSG:
                    if (genericListener != null) {

                        genericListener.responseDataParsedFromGenericReader((ResponseMsg) msg);
                    }
                    break;
                case NOTIFICATION:
                    if (genericListener != null) {
                        genericListener.notificationFromGenericReader((Notification) msg);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void dataReceivedFromBluetooth(String data) {
        Constants.logAsMessage(Constants.TYPE_DEBUG, "data from reader ", data);
        if (null != data && !data.isEmpty()) {
            //Parse the data
            try {
                IMsg msg = ASCIIProcessor.getReplyMsg(data, this.metaData);
                getReplyDetails(msg);

            } catch (Exception e) {
                Log.e(GenericReader.class.getName(), e.toString());
            }
        }
    }

    /**
     * This interface should be implemented by all the activities which
     * want to use the data from the reader in the UI
     */
    public interface GenericReaderResponseParsedListener {
        /**
         * method to handle parsed data from {@link GenericReader}
         *
         * @param msg response data from reader
         */
        void responseDataParsedFromGenericReader(ResponseMsg msg);

        /**
         * method to handle notifications received from reader
         *
         * @param notification notification from reader
         */
        void notificationFromGenericReader(Notification notification);

        /**
         * method to handle configuration retrieved from reader
         *
         * @param msg reader configuration
         */
        void configurationsFromReader(IMsg msg);
    }

    public interface ResponseTagHandler {
        /**
         * method to handle tag data
         *
         * @param inventoryListItem - tag data
         * @param isAddedToList     - true if tag is added to the list.
         *                          - false if tag is updated in the list.
         */
        void handleTagResponse(InventoryListItem inventoryListItem, boolean isAddedToList);
    }
    public interface TriggerEventHandler {

        /**
         * method to handle trigger press event
         */
        void triggerPressEventRecieved();

        /**
         * method to handle trigger release event
         */
        void triggerReleaseEventRecieved();
    }
}
