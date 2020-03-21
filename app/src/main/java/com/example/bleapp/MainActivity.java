package com.example.bleapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

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
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

public class MainActivity extends AppCompatActivity {
    final String DEVICE_NAME = "CryptoKit";

    TextView resultsView;
    Button startScanningButton;
    BLEGPConnection bleGPConn = null;

    private enum ButtonState
    {
        E_START_SCAN,
        E_STOP_SCAN,
        E_DISCONNECT
    };

    private ButtonState btnState = ButtonState.E_START_SCAN;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Password field */
        final EditText txtPassword = (EditText) this.findViewById(R.id.txtPassword);

        /* Load password if exists */
        txtPassword.setText(CommPasswordManager.getInstance().loadPassword(this));


        /* Register save event */
        txtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                Log.d("BLEApp", "[PASSWD] Key stroke");

                /* Save content to file */
                CommPasswordManager.getInstance().savePassword(MainActivity.this, txtPassword.getText().toString());
            }
        });

        resultsView = (TextView) this.findViewById(R.id.resultsView);
        resultsView.setMovementMethod(new ScrollingMovementMethod());

        EditText txtPayloadSend = MainActivity.this.findViewById(R.id.txtPayloadSend);
        txtPayloadSend.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == event.KEYCODE_ENTER)
                {
                    Button btnSend = MainActivity.this.findViewById(R.id.btnPayloadSend);
                    btnSend.callOnClick();
                }

                return true;
            }
        });

        /* Initialize the stack */
        BLEGPStackComm.getInstance().initialize(this);

        /* Create callbacks */
        final BLEAppCallback connectionCallback =
            new BLEAppCallback() {
                @Override
                public void BLEDisconnected(BLEGPConnection conn)
                {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnState = ButtonState.E_START_SCAN;
                            startScanningButton.setText("Scan");
                        }
                    });
                }

                @Override
                public void BLEConnectionFailed() {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnState = ButtonState.E_START_SCAN;
                            startScanningButton.setText("Scan");
                        }
                    });
                }

                @Override
                public void BLEConnectionSuccess(final BLEGPConnection conn) {
                    /* Save connection */
                    bleGPConn = conn;

                    /* Change button (*/
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultsView.append(String.format("Connected to device %s\n", conn.getDevice().getName()));
                            resultsView.append(String.format("\t\tAddress: %s\n", conn.getDevice().getAddress()));

                            btnState = ButtonState.E_DISCONNECT;
                            startScanningButton.setText("Disconnect");
                        }
                    });
                }

                @Override
                public void BLEReceiveData(BLEGPConnection conn, byte[] data) {
                    /* Implement app logic */
                }
            };

        startScanningButton = (Button) findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            public void onClick(View v) {
                if (btnState == ButtonState.E_START_SCAN) {
                    btnState = ButtonState.E_STOP_SCAN;
                    startScanningButton.setText("Stop Scanning");

                    resultsView.setText("Starting scan (v3)...\n");

                    /* Connect to the device */
                    BLEGPStackComm.getInstance().Connect(DEVICE_NAME, connectionCallback);
                }
                else if (btnState == ButtonState.E_STOP_SCAN)
                {
                    btnState = ButtonState.E_START_SCAN;
                    startScanningButton.setText("Scan");

                    resultsView.append("Stopped Scanning\n");

                    bleGPConn.Disconnect();

                    bleGPConn = null;
                }
                else if (btnState == ButtonState.E_DISCONNECT)
                {
                    btnState = ButtonState.E_START_SCAN;
                    startScanningButton.setText("Scan");

                    resultsView.append("Disconnected.\n");

                    BLEFragmentComm.getInstance().reset();
                }
            }
        });

        Button btnSend = this.findViewById(R.id.btnPayloadSend);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bleGPConn != null)
                {
                    resultsView.append("Sending payload...\n");
                    EditText txtPayloadSend = MainActivity.this.findViewById(R.id.txtPayloadSend);
                    String val = txtPayloadSend.getText().toString();

                    bleGPConn.Send(val.getBytes());

                    /* Clear */
                    txtPayloadSend.setText("");
                }
            }
        });
    }
}
