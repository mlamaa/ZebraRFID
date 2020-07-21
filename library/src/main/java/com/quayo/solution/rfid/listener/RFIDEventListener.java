package com.quayo.solution.rfid.listener;

import java.util.List;

public interface RFIDEventListener {
    void triggerPressEvent();
    void triggerReleaseEvent(List<String> inventoryItems);
}
