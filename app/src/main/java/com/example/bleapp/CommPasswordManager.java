package com.example.bleapp;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CommPasswordManager {
    private final String passwordFile = "CommPassword";

    private static CommPasswordManager _instance = null;

    private CommPasswordManager()
    {
        /* Do nothing */
    }

    public byte[] getCommKey(byte[] data)
    {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA256");
            md.update(data);
            return md.digest();
        }
        catch (NoSuchAlgorithmException e)
        {
            Log.d("BLEApp", "[PASSWD] No such algorithm SHA256");
        }

        return null;
    }

    public static CommPasswordManager getInstance()
    {
        if (_instance == null)
            _instance = new CommPasswordManager();

        return _instance;
    }

    public void savePassword(Context c, String password)
    {
        try
        {
            FileOutputStream fs = c.openFileOutput(passwordFile, Context.MODE_PRIVATE);
            fs.write(password.getBytes());
            fs.flush();
            fs.close();
        }
        catch (FileNotFoundException e)
        {
        }
        catch (IOException e)
        {
        }
    }

    public String loadPassword(Context c)
    {
        File f = new File(c.getFilesDir() + "/" + passwordFile);

        if (!f.exists()) {
            Log.d("BLEApp", "[PASSWD] Password file does not exist");
            return "";
        }

        /* Allocate room for data */
        byte[] data = new byte[(int)f.length()];

        try
        {
            /* Read the password */
            FileInputStream fs = c.openFileInput(passwordFile);

            fs.read(data, 0, (int)f.length());

            fs.close();

        }
        catch (FileNotFoundException e)
        {
        }
        catch (IOException e)
        {
        }

        return new String(data);
    }
}
