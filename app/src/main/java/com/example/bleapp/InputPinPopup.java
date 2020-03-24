package com.example.bleapp;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.media.MediaDrm;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.HashMap;

public class InputPinPopup extends Activity {
    private static final int VERIFY_PIN_REQUEST_CODE = 0;

    public enum InputPurpose
    {
        E_INITIAL_SETUP,
        E_INITIAL_SETUP_VERIFY,
        E_LOGIN_INPUT
    };

    private InputPurpose state;
    private final float POPUP_PERCENTAGE = (float) 0.8;
    private final int DIGITS_NUM = 6;

    private EditText[] digits;
    private HashMap<Integer, Integer> idsMap;
    private HashMap<Integer, EditText> nextMap;
    private HashMap<Integer, EditText> previousMap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.inputpinpopup);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = dm.widthPixels;
        int height = dm.heightPixels;

        /* Setup the UI according to state */
        Intent startIntent = getIntent();
        state = (InputPurpose)getIntent().getSerializableExtra("State");
        TextView description = (TextView)findViewById(R.id.pinExplainView);
        Button btnSubmit = (Button)findViewById(R.id.btnPinSubmit);
        Button btnClear = (Button)findViewById(R.id.btnPinClear);

        /* Clear PIN */
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < DIGITS_NUM; ++i)
                {
                    digits[i].setText("");
                }

                focusPin(digits[0]);
            }
        });

        /* Disable submit */
        btnSubmit.setEnabled(false);

        switch (state)
        {
            case E_INITIAL_SETUP:
            {
                description.setText(String.format("Welcome. To start using CryptoKit, please enter a %d-digit PIN code. This PIN code will be used to access the device and will be the basis for protecting your wallet's private key.", DIGITS_NUM));

                btnSubmit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent verifyPinIntent = new Intent(InputPinPopup.this, InputPinPopup.class);
                        verifyPinIntent.putExtra("State", InputPurpose.E_INITIAL_SETUP_VERIFY);
                        verifyPinIntent.putExtra("verifyPin", getTypedPin());

                        startActivityForResult(verifyPinIntent, VERIFY_PIN_REQUEST_CODE);
                    }
                });

                break;
            }

            case E_INITIAL_SETUP_VERIFY:
            {
                description.setText("Please enter the PIN code again, for verification purposes.");

                btnSubmit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String verifyPin = getIntent().getStringExtra("verifyPin");

                        if (!verifyPin.equals(getTypedPin())) {
                            Toast.makeText(InputPinPopup.this, "Pins do not match", Toast.LENGTH_SHORT).show();

                            return;
                        }

                        Toast.makeText(InputPinPopup.this, "Success", Toast.LENGTH_SHORT).show();

                        Intent verifySuccess = new Intent();

                        verifySuccess.putExtra("Result", verifyPin);

                        setResult(RESULT_OK, verifySuccess);
                        finish();
                    }
                });

                break;
            }

            case E_LOGIN_INPUT:
            {
                description.setText("Enter PIN code for login");

                btnSubmit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent loginResult = new Intent();
                        loginResult.putExtra("Result", getTypedPin());

                        setResult(RESULT_OK, loginResult);
                        finish();
                    }
                });

                break;
            }
        }

        getWindow().setLayout((int)(width * POPUP_PERCENTAGE), (int)(height * POPUP_PERCENTAGE));

        digits = new EditText[DIGITS_NUM];
        digits[0] = findViewById(R.id.pinDigit0);
        digits[1] = findViewById(R.id.pinDigit1);
        digits[2] = findViewById(R.id.pinDigit2);
        digits[3] = findViewById(R.id.pinDigit3);
        digits[4] = findViewById(R.id.pinDigit4);
        digits[5] = findViewById(R.id.pinDigit5);

        idsMap = new HashMap<>();

        for (int i = 0; i < DIGITS_NUM; ++i)
        {
            idsMap.put(digits[i].getId(), i);
        }

        /* Add the next-mapping */
        nextMap = new HashMap<>();

        for (int i = 0; i < DIGITS_NUM; ++i)
        {
            if (i + 1 < DIGITS_NUM)
                nextMap.put(digits[i].getId(), digits[i + 1]);
            else
                nextMap.put(digits[i].getId(), null);
        }

        previousMap = new HashMap<>();

        for (int i = 0; i < DIGITS_NUM; ++i)
        {
            if (i == 0)
                previousMap.put(digits[i].getId(), null);
            else
                previousMap.put(digits[i].getId(), digits[i - 1]);
        }

        View.OnKeyListener pinKey = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (v == null) {
                    Log.d("BLEApp", "[PIN] NULL Key stroke");
                    return true;
                }

                if (event.getAction() == KeyEvent.ACTION_UP) {
                    EditText pinDigit = (EditText)v;
                    String data = pinDigit.getText().toString();

                    if ((data.length() > 0) && (keyCode != KeyEvent.KEYCODE_DEL)) {
                        /* Make sure there's only one digit in the field */
                        if (data.length() > 1)
                            pinDigit.setText(data.substring(0, 1));

                        EditText nextDigit = nextMap.get(pinDigit.getId());

                        /* Set focus on the next one */
                        if (nextDigit != null)
                        {
                            focusPin(nextDigit);
                        }
                        else
                        {
                            /* Close keyboard */
                            InputMethodManager imm = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(pinDigit.getWindowToken(), 0);
                        }
                    }
                    else
                    {
                        if ((pinDigit.getId() == digits[DIGITS_NUM - 1].getId()) && (pinDigit.getText().toString().length() != 0))
                            return false;

                        /* digits erased, go back */
                        EditText prevDigit = previousMap.get(pinDigit.getId());

                        if (prevDigit != null)
                        {
                            prevDigit.setText("");
                            focusPin(prevDigit);
                        }

                    }
                }

                Button btnSubmit = (Button)findViewById(R.id.btnPinSubmit);

                /* Enable the button when finished */
                if (getTypedPin().length() == DIGITS_NUM)
                {
                    btnSubmit.setEnabled(true);
                }
                else
                {
                    btnSubmit.setEnabled(false);
                }

                return false;
            }
        };

        View.OnFocusChangeListener pinFocus = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v == null)
                    return;

                InputMethodManager imm = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, 0);
            }
        };

        /* Setup all digits */
        for (int i = 0; i < DIGITS_NUM; ++i)
        {
            digits[i].setOnKeyListener(pinKey);
            digits[i].setOnFocusChangeListener(pinFocus);
        }

        /* Focus on the first digit */
        focusPin(digits[0]);
    }

    private String getTypedPin()
    {
        String s = "";

        for (int i = 0; i < DIGITS_NUM; ++i)
        {
            String digit = digits[i].getText().toString();

            if (digit.equals(""))
                break;

            s += digit;
        }

        return s;
    }

    private void focusPin(EditText pinDigit)
    {
        pinDigit.setFocusable(true);
        pinDigit.setFocusableInTouchMode(true);
        pinDigit.requestFocus();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VERIFY_PIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                /* Propagate the result - This is the initial PIN activity */
                setResult(RESULT_OK, data);
                finish();
            }
        }
    }
}
