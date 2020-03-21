package com.example.bleapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.List;

import javax.crypto.IllegalBlockSizeException;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

public class BLEGPStackComm
{
    final private int SCAN_TIMEOUT = 20000;
    private enum scanning_state
    {
        E_SCANNING,
        E_NOT_SCANNING
    }


    BluetoothLeScanner btScanner;

    private static BLEGPStackComm _instance = null;

    private scanning_state state = scanning_state.E_NOT_SCANNING;
    private boolean foundWriteDescriptor;
    private Context appContext = null;
    private String scannedDevice;
    private ScanCallback bleScanCallback;
    private ScanCallback bleStopScan;
    private BluetoothGattCallback bleGattCallback;
    private BLEAppCallback appCallback;
    private HashMap<String, BLEGPConnection> connections;
    BluetoothManager btManager;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private BLEGPStackComm() {
        connections = new HashMap<>();

        byte[] iv = new byte[BLECipherComm.getInstance().AES_BLOCK_SIZE];
        for (int i = 0; i < BLECipherComm.getInstance().AES_BLOCK_SIZE; ++i)
        {
            iv[i] = 0;
        }

        try {
            BLECipherComm.getInstance().initIV(iv);
        } catch (IllegalBlockSizeException e) {
            Log.d("BLE", "[Cipher] Invalid IV size");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static BLEGPStackComm getInstance()
    {
        if (_instance == null)
            _instance = new BLEGPStackComm();

        return _instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initialize(Context c)
    {
        appContext = c;

        btManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        btScanner = btManager.getAdapter().getBluetoothLeScanner();

        bleScanCallback = new ScanCallback() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onScanResult(int callbackType, final ScanResult result) {
                BluetoothDevice device = result.getDevice();

                if (device == null)
                    return;

                /* Don't do anything further if already connected... */
                if ((state == scanning_state.E_NOT_SCANNING) || (connections.get(device.getAddress()) != null)) {
                    return;
                }

                if ((device.getName() != null) && (device.getName().equals(scannedDevice))) {
                    stopScanning();

                    Log.d("BLE", String.format("[BLE] Found device %s\n", device.getName()));
                    Log.d("BLE", String.format("[BLE] \t\tAddress: %s\n", device.getAddress()));

                    device.connectGatt(appContext, true, bleGattCallback, TRANSPORT_LE);
                }
            }
        };

        bleStopScan = new ScanCallback() {
            @Override
            public void onScanFailed(int errorCode)
            {
                Log.d("BLE", "Done scanning (Failed).\n");
            }

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d("BLE", "Done scanning.\n");
            }
        };

        bleGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.d("BLE", String.format("[BLE] Connection state changed. Status = %d newState = %d\n", status, newState));

                if (newState == 0)
                {
                    appCallback.BLEDisconnected(connections.get(gatt.getDevice().getAddress()));
                    connections.get(gatt.getDevice().getAddress()).Disconnect();
                    return;
                }

                /* Close connection */
                if (status == 133)
                {
                    gatt.close();

                    if (!gatt.connect())
                    {
                        Log.d("BLEApp", "[BLE] Failed connecting...");

                        appCallback.BLEConnectionFailed();

                        return;
                    }
                }

                Log.d("BLE", "[BLE] Discovering services...\n");
                gatt.discoverServices();
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                Log.d("BLE", String.format("[BLE]\t\tMTU changed to %d\n", mtu));
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status)
            {
                List<BluetoothGattService> services = gatt.getServices();
                Log.d("BLE", String.format("[BLE] Getting services... (%d)\n", services.size()));
                BluetoothGattCharacteristic bleTxCharacteristic = null;

                if (services == null) {
                    Log.d("BLE","[BLE] No given services.\n");

                    return;
                }

                for (BluetoothGattService s : services) {
                    Log.d("BLE", String.format("[BLE]\t\t\t\tService: %x\n", s.getUuid().getMostSignificantBits() >> 32));
                    List<BluetoothGattCharacteristic> chars = s.getCharacteristics();

                    for (BluetoothGattCharacteristic c : chars) {
                        Log.d("BLE", String.format("[BLE]\t\t\t\t\t\tCharacteristic: %x\n", c.getUuid().getMostSignificantBits() >> 32));

                        if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0)
                        {
                            /* Setup lower layer connection parameters */
                            BLEFragmentComm.getInstance().setupConnection(gatt, c);
                            bleTxCharacteristic = c;

                            Log.d("BLE", "[BLE]\t\t\t\t\t\t\t\tFound writable characteristic.\n");
                        }
                        else if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) != 0)
                        {
                            foundWriteDescriptor = false;

                            final String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
                            gatt.setCharacteristicNotification(c, true);

                            for (BluetoothGattDescriptor d : c.getDescriptors())
                            {
                                Log.d("BLE", String.format("[BLE]\t\t\t\t\t\t\t\tDescriptor: %x\n", d.getUuid().getMostSignificantBits() >> 32));

                                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(d);
                                foundWriteDescriptor = true;
                            }

                            if ((foundWriteDescriptor) && (bleTxCharacteristic != null))
                            {
                                /* Connection established. Create connection object */
                                BLEGPConnection conn = new BLEGPConnection(appContext, gatt.getDevice(), gatt, bleTxCharacteristic, appCallback);

                                Log.d("BLE", String.format("[BLE]\t\t\t\t\t\t\t\tConnection success: %s.\n", conn.getDevice().getAddress()));

                                /* Save as a connection */
                                connections.put(gatt.getDevice().getAddress(), conn);

                                appCallback.BLEConnectionSuccess(connections.get(gatt.getDevice().getAddress()));

                            }
                            else
                            {
                                /* Not the right connection */
                                gatt.disconnect();

                                /* Raise connection failure */
                                appCallback.BLEConnectionFailed();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
                /* Up the stack */
                connections.get(gatt.getDevice().getAddress()).Receive(characteristic.getStringValue(0).getBytes());
            }

            @Override
            public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
            {
                Log.d("BLE", String.format("[BLE]\t\t\t\tCharacteristic: %s\n", characteristic.getUuid()));
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d("BLE", String.format("[BLE]\t\t\t\tNew descriptor: %s\n", descriptor.getUuid()));
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopScanning()
    {
        Log.d("BLE", "[BLE] Scanning stopped.");

        state = scanning_state.E_NOT_SCANNING;

        btScanner.stopScan(bleStopScan);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void Connect(String devName, BLEAppCallback bleCallback)
    {
        appCallback = bleCallback;
        scannedDevice = devName;

        btScanner.startScan(bleScanCallback);
        state = scanning_state.E_SCANNING;

        /* Create timeout */
        new Thread() {
            public void run()
            {
                try {
                    Thread.sleep(SCAN_TIMEOUT);

                    /* If within the timeout device was not found, raise connection failed callback */
                    if (!foundWriteDescriptor)
                    {
                        Log.d("BLE", "[BLE] Failed finding device. Stopping scan.");
                        stopScanning();

                        appCallback.BLEConnectionFailed();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public void Disconnect(BLEGPConnection conn)
    {
        /* Call disconnect */
        this.appCallback.BLEDisconnected(this.connections.get(conn.getDevice().getAddress()));

        this.connections.remove(conn.getDevice().getAddress());

        Log.d("BLE", String.format("[BLE] Disconnected device: %s", conn.getDevice().getAddress()));
    }
}
