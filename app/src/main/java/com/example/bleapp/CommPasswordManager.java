package com.example.bleapp;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static com.example.bleapp.BLECipherComm.AES_BLOCK_SIZE;

public class CommPasswordManager {
    private static final String pinSalt = "EncryptCommKey";
    private static final String passwordFile = "CommPassword";
    private static final int DECRYPTION_SUCCESS_CODE = 0x42;

    private static CommPasswordManager _instance = null;

    private CommPasswordManager()
    {
        /* Do nothing */
    }

    public byte[] getCommKey(byte[] data)
    {
        return hash(data);
    }

    private byte[] hash(byte[] data)
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

    private byte[] decrypt(byte[] ciphertext, byte[] aesKey) throws InvalidKeyException {
        byte[] aesIv = new byte[AES_BLOCK_SIZE];
        for (int i = 0; i < AES_BLOCK_SIZE; ++i)
            aesIv[0] = 0;

        try {
            Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
            aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES256"), new IvParameterSpec(aesIv));

            byte[] plaintext = aesCipher.doFinal(ciphertext);

            if (plaintext[1] != DECRYPTION_SUCCESS_CODE)
                throw new InvalidKeyException("Invalid key input");

            int data_len = plaintext.length - 2 - plaintext[0];

            /* Copy only data */
            byte[] data = new byte[data_len];

            System.arraycopy(plaintext, 2, data, 0, data_len);

            // Log.d("BLEApp", "[AES] Successful encryption");

            return data;
        } catch (NoSuchAlgorithmException e) {
            Log.d("BLEApp", "[AES] No such algorithm AES/CBC");
        } catch (NoSuchPaddingException e) {
            Log.d("BLEApp", "[AES] No such padding (??)");
        } catch (BadPaddingException e) {
            Log.d("BLEApp", "[AES] Bad Padding");
        } catch (IllegalBlockSizeException e) {
            Log.d("BLEApp", "[AES] Illegal block size");
        } catch (InvalidAlgorithmParameterException e) {
            Log.d("BLEApp", "[AES] Invalid IV parameter");
        }

        return null;
    }

    private byte[] encrypt(byte[] data, byte[] aesKey)
    {
        byte[] aesIv = new byte[AES_BLOCK_SIZE];
        for (int i = 0; i < AES_BLOCK_SIZE; ++i)
            aesIv[0] = 0;

        /* Create padded buffer */
        int len = data.length + 2; /* + 1 for padding byte + 1 for success code*/
        byte pad = 0;

        if (len % AES_BLOCK_SIZE != 0)
            pad = (byte)(AES_BLOCK_SIZE - (len % AES_BLOCK_SIZE));

        byte[] plaintext = new byte[len + pad];

        plaintext[0] = pad;
        plaintext[1] = DECRYPTION_SUCCESS_CODE; /* Success code */

        /* Copy buffer */
        System.arraycopy(data, 0, plaintext, 2, data.length);

        /* Encrypt */
        try {
            Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
            aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES256"), new IvParameterSpec(aesIv));

            byte[] ciphertext = aesCipher.doFinal(plaintext);

            return ciphertext;
        } catch (NoSuchAlgorithmException e) {
            Log.d("BLEApp", "[AES] No such algorithm AES/CBC");
        } catch (NoSuchPaddingException e) {
            Log.d("BLEApp", "[AES] No such padding (??)");
        } catch (InvalidKeyException e) {
            Log.d("BLEApp", "[AES] Invalid key");
        } catch (BadPaddingException e) {
            Log.d("BLEApp", "[AES] Bad Padding");
        } catch (IllegalBlockSizeException e) {
            Log.d("BLEApp", "[AES] Illegal block size");
        } catch (InvalidAlgorithmParameterException e) {
            Log.d("BLEApp", "[AES] Invalid IV parameter");
        }

        return null;
    }

    public void savePassword(Context c, String password, String pin)
    {
        /* Calculate the pin SHA256 + salt*/
        String saltedPin = pin + pinSalt;

        byte[] encryptedPassword = encrypt(password.getBytes(), hash(saltedPin.getBytes()));

        try
        {
            FileOutputStream fs = c.openFileOutput(passwordFile, Context.MODE_PRIVATE);
            fs.write(encryptedPassword);
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

    public String loadPassword(Context c, String pin) throws FileNotFoundException, InvalidKeyException {
        File f = new File(c.getFilesDir() + "/" + passwordFile);

        if (!f.exists()) {
            throw new FileNotFoundException();
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

        /* Calculate the pin SHA256 + salt*/
        String saltedPin = pin + pinSalt;

        byte[] decryptedData = decrypt(data, hash(saltedPin.getBytes()));

        return new String(decryptedData);
    }

    public boolean isInitialized(Context c)
    {
        return new File(c.getFilesDir() + "/" + passwordFile).exists();
    }

    public void reset(Context c)
    {
        File f = new File(c.getFilesDir() + "/" + passwordFile);

        if (!f.exists()) {
            return;
        }

        f.delete();
    }
}
