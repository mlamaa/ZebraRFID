package com.quayo.solution.rfid.listener;

import com.motorolasolutions.ASCII_SDK.Response_TagProximityPercent;

public interface OnProximityChangeListener {

    void onChange(Response_TagProximityPercent proximityPercent);
}
