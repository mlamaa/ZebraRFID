package com.quayo.solution.rfid.listener;

public interface ScanListener {
    void handleScannedItem(String barcode);
}
