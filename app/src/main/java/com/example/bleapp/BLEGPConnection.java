package com.example.bleapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class BLEGPConnection {
    private Context appContext;
    private BLEAppCallback appCallback;
    private BluetoothDevice bleDevice;
    private BluetoothGatt bleGattConn;
    private BluetoothGattCharacteristic bleTxCharacteristic;

    public BLEGPConnection(Context c, BluetoothDevice device, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, BLEAppCallback callback)
    {
        appContext = c;
        appCallback = callback;
        bleDevice = device;
        bleGattConn = gatt;
        bleTxCharacteristic = characteristic;
    }

    public BluetoothDevice getDevice()
    {
        return bleDevice;
    }

    public BluetoothGatt getBleGattConnection()
    {
        return bleGattConn;
    }

    public BluetoothGattCharacteristic getBleTxCharacteristic()
    {
        return bleTxCharacteristic;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void Disconnect()
    {
        if (bleGattConn != null)
            bleGattConn.disconnect();

        BLEGPStackComm.getInstance().Disconnect(this);
        bleGattConn = null;
        bleTxCharacteristic = null;
    }

    public void Receive(byte[] data)
    {
        byte[] reassembled = BLEFragmentComm.getInstance().ReassembleFragment(data);

        if (reassembled == null)
        {
            /* Either error or incomplete packet */
            return;
        }

        /* Full packet. Decrypt. */
        byte[] plaintext = BLECipherComm.getInstance().decryptMessage(CommPasswordManager.getInstance().getCommKey(CommPasswordManager.getInstance().loadPassword(appContext).getBytes()), reassembled);

        /* Send to upper layer */
        appCallback.BLEReceiveData(this, plaintext);
    }

    public void Send(byte[] data)
    {
        /* First, encrypt */
        byte[] ciphertext = BLECipherComm.getInstance().encryptMessage(CommPasswordManager.getInstance().getCommKey(CommPasswordManager.getInstance().loadPassword(appContext).getBytes()), data);

        /* Then, fragment */
        final byte[][] fragments = BLEFragmentComm.getInstance().FragmentData(ciphertext);

        new Thread() {
            public void run()
            {
                for (byte[] fragment : fragments) {
                    bleTxCharacteristic.setValue(fragment);
                    bleTxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    bleGattConn.writeCharacteristic(bleTxCharacteristic);

                    /* Create a small delay to give the device time to update */
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                }
            }
        }.start();

    }
}
