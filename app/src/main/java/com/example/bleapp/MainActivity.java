package com.example.bleapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
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
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.security.InvalidKeyException;

import javax.crypto.IllegalBlockSizeException;

public class MainActivity extends AppCompatActivity {
    public static final int INITIAL_SETUP_REQUEST_CODE = 0;
    public static final int LOGIN_REQUEST_CODE = 1;

    private final String DEVICE_NAME = "CryptoKit";

    TextView resultsView;
    Button startScanningButton;
    BLEGPConnection bleGPConn = null;
    String pin = null;

    private enum ButtonState
    {
        E_START_SCAN,
        E_STOP_SCAN,
        E_DISCONNECT
    };

    private ButtonState btnState = ButtonState.E_START_SCAN;

    private void requestLoginPin()
    {
        Intent newPinIntent = new Intent(this, InputPinPopup.class);
        newPinIntent.putExtra("State", InputPinPopup.InputPurpose.E_LOGIN_INPUT);

        startActivityForResult(newPinIntent, LOGIN_REQUEST_CODE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // CommPasswordManager.getInstance().reset(this);

        if (!CommPasswordManager.getInstance().isInitialized(this))
        {
            Log.d("BLEApp", "[PIN] Setup");

            /* If this is the first time using the application, prompt for generating a password */
            Intent newPinIntent = new Intent(this, InputPinPopup.class);
            newPinIntent.putExtra("State", InputPinPopup.InputPurpose.E_INITIAL_SETUP);

            startActivityForResult(newPinIntent, INITIAL_SETUP_REQUEST_CODE);
        }
        else
        {
            requestLoginPin();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initSuccessfulPin() throws InvalidKeyException
    {
        /* Password field */
        final EditText txtPassword = (EditText) this.findViewById(R.id.txtPassword);

        /* Load password if exists */
        try
        {
            txtPassword.setText(CommPasswordManager.getInstance().loadPassword(this, pin));
        }
        catch (FileNotFoundException e)
        {
            /* Do nothing for now... */
        }

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
                CommPasswordManager.getInstance().savePassword(MainActivity.this, txtPassword.getText().toString(), pin);
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
                            resultsView.append("Connection failed.\n");
                            btnState = ButtonState.E_START_SCAN;
                            startScanningButton.setText("Scan");
                        }
                    });
                }

                @Override
                public void BLEConnectionSuccess(final BLEGPConnection conn) {
                    /* Save connection */
                    bleGPConn = conn;

                    /* Initialize IV and Key */
                    try {
                        byte[] iv = new byte[BLECipherComm.AES_BLOCK_SIZE];
                        for (int i = 0; i < BLECipherComm.AES_BLOCK_SIZE; ++i)
                        {
                            iv[i] = 0;
                        }

                        conn.getCipher().initIV(iv);
                    } catch (IllegalBlockSizeException e) {
                        Log.d("BLE", "[Cipher] Invalid IV size");
                    }

                    try {
                        conn.getCipher().initKey(CommPasswordManager.getInstance().getCommKey(CommPasswordManager.getInstance().loadPassword(MainActivity.this, pin).getBytes()));
                    } catch (InvalidKeyException e) {
                        Log.d("BLE", "[Cipher] Invalid AES256 Key size");
                    }
                    catch (FileNotFoundException e)
                    {
                        Log.d("BLE", "[Cipher] Password was not initialized.");
                    }

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

                    resultsView.setText("Starting scan (v4)...\n");

                    /* Connect to the device */
                    BLEGPStackComm.getInstance().Connect(DEVICE_NAME, connectionCallback);
                }
                else if (btnState == ButtonState.E_STOP_SCAN)
                {
                    btnState = ButtonState.E_START_SCAN;
                    startScanningButton.setText("Scan");

                    resultsView.append("Stopped Scanning\n");

                    if (bleGPConn != null)
                        bleGPConn.Disconnect();

                    bleGPConn = null;
                }
                else if (btnState == ButtonState.E_DISCONNECT)
                {
                    btnState = ButtonState.E_START_SCAN;
                    startScanningButton.setText("Scan");

                    resultsView.append("Disconnected.\n");

                    if (bleGPConn != null)
                        bleGPConn.Disconnect();

                    bleGPConn = null;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INITIAL_SETUP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                pin = data.getStringExtra("Result");

                try {
                    initSuccessfulPin();
                } catch (InvalidKeyException e) {
                    /* First time it won't happen...*/
                }
            }
        }
        else if (requestCode == LOGIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                pin = data.getStringExtra("Result");

                try {
                    initSuccessfulPin();
                } catch (InvalidKeyException e) {
                    /* Pin was wrong. */
                    pin = null;

                    Toast.makeText(this, "Invalid PIN", Toast.LENGTH_SHORT).show();

                    /* If decryption failed, request pin AGAIN */
                    requestLoginPin();
                }
            }
        }
    }
}
