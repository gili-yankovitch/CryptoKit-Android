package com.example.bleapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;


public abstract class BLEAppCallback {
    public abstract void BLEDisconnected(BLEGPConnection conn);
    public abstract void BLEConnectionFailed();
    public abstract void BLEConnectionSuccess(BLEGPConnection conn);
    public abstract void BLEReceiveData(BLEGPConnection conn, byte[] data);
}
