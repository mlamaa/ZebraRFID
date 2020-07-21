package com.quayo.solution.rfid.task;

import android.os.AsyncTask;

import com.motorolasolutions.ASCII_SDK.AcessOperation;
import com.motorolasolutions.ASCII_SDK.Response_TagData;
import com.quayo.solution.rfid.Connector;
import com.quayo.solution.rfid.Constants;
import com.quayo.solution.rfid.InventoryListItem;

public class ResponseHandlerTask extends AsyncTask<Void, Void, Boolean> {

    private final Connector connector;
    private Response_TagData response_tagData;
    private InventoryListItem inventoryItem;
    private InventoryListItem oldObject;

    public ResponseHandlerTask(Connector connector, Response_TagData response_tagData) {
        this.connector = connector;
        this.response_tagData = response_tagData;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        boolean added = false;
        try {
            if (Connector.inventoryList.containsKey(response_tagData.EPCId)) {
                inventoryItem = new InventoryListItem(response_tagData.EPCId, 1, null, null, null, null, null, null);
                int index = Connector.inventoryList.get(response_tagData.EPCId);
                if (index >= 0) {
                    Connector.TOTAL_TAGS++;
                    //Tag is already present. Update the fields and increment the count
                  //  if (response_tagData.tagAcessOprations != null)
//                            for (AcessOperation acessOperation : response_tagData.tagAcessOprations) {
//                                if (acessOperation.opration.equalsIgnoreCase("read")) {
////                                    memoryBank = acessOperation.memoryBank;
////                                    memoryBankData = acessOperation.memoryBankData;
//                                }
//                            }

                        oldObject = Connector.tagsReadInventory.get(index);
                    if (oldObject != null) {
                        oldObject.incrementCount();
//                        if (oldObject.getMemoryBankData() != null && !oldObject.getMemoryBankData().equalsIgnoreCase(memoryBankData))
//                            oldObject.setMemoryBankData(memoryBankData);
//                        //oldObject.setEPCId(inventoryItem.getEPCId());

                        oldObject.setPC(response_tagData.PC);
                        oldObject.setPhase(response_tagData.Phase);
                        oldObject.setChannelIndex(response_tagData.ChannelIndex);
                        oldObject.setRSSI(response_tagData.RSSI);
                    }
                }
            } else {
                //Tag is encountered for the first time. Add it.
                if (Connector.inventoryMode == 0 || (Connector.inventoryMode == 1 && Connector.UNIQUE_TAGS <= Constants.UNIQUE_TAG_LIMIT)) {
                    int tagSeenCount = 0;
                    if (response_tagData.TagSeenCount != null)
                        tagSeenCount = Integer.parseInt(response_tagData.TagSeenCount);
                    if (tagSeenCount != 0) {
                        Connector.TOTAL_TAGS += tagSeenCount;
                        inventoryItem = new InventoryListItem(response_tagData.EPCId, tagSeenCount, null, null, null, null, null, null);
                    } else {
                        Connector.TOTAL_TAGS++;
                        inventoryItem = new InventoryListItem(response_tagData.EPCId, 1, null, null, null, null, null, null);
                    }
                    added = Connector.tagsReadInventory.add(inventoryItem);
                    if (added) {
                        Connector.inventoryList.put(response_tagData.EPCId, Connector.UNIQUE_TAGS);
                        if (response_tagData.tagAcessOprations != null)
                            for (AcessOperation acessOperation : response_tagData.tagAcessOprations) {
                                if (acessOperation.opration.equalsIgnoreCase("read")) {
//                                        memoryBank = acessOperation.memoryBank;
//                                        memoryBankData = acessOperation.memoryBankData;
                                }
                            }
                        oldObject = Connector.tagsReadInventory.get(Connector.UNIQUE_TAGS);
//                            oldObject.setMemoryBankData(memoryBankData);
//                            oldObject.setMemoryBank(memoryBank);
                        oldObject.setPC(response_tagData.PC);
                        oldObject.setPhase(response_tagData.Phase);
                        oldObject.setChannelIndex(response_tagData.ChannelIndex);
                        oldObject.setRSSI(response_tagData.RSSI);

                        Connector.UNIQUE_TAGS++;
                    }
                }

            }
        } catch (IndexOutOfBoundsException e) {

            oldObject = null;
            added = false;
        } catch (Exception e) {

            oldObject = null;
            added = false;
        }
        response_tagData = null;
        inventoryItem = null;
//            memoryBank = null;
//            memoryBankData = null;
        return added;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        cancel(true);
        if (oldObject != null)
            connector.handleTagResponse(oldObject, result);
        oldObject = null;
    }

}
