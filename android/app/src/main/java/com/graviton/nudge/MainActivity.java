package com.graviton.nudge;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NudgeNative.class);
        super.onCreate(savedInstanceState);
    }
}
