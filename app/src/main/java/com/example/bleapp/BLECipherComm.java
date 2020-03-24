package com.example.bleapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BLECipherComm {
    public static final int AES_BLOCK_SIZE = 16;
    public static final int AES256_KEY_SIZE = 32;
    byte[] aesKey;
    byte[] aesIv;

    public BLECipherComm()
    {
        aesIv = new byte[AES_BLOCK_SIZE];
    }

    /*
        private static BLECipherComm _instance = null;
        public static BLECipherComm getInstance()
        {
            if (_instance == null)
                _instance = new BLECipherComm();

            return _instance;
        }
    */
    public void initIV(byte[] iv) throws IllegalBlockSizeException {
        if (iv.length != AES_BLOCK_SIZE)
            throw new IllegalBlockSizeException();

        aesIv = iv;
    }

    public void initKey(byte[] key) throws InvalidKeyException {
        if (key.length != AES256_KEY_SIZE)
            throw new InvalidKeyException();

        aesKey = key;
    }


    public byte[] decryptMessage(byte[] ciphertext)
    {
        try {
            Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
            aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES256"), new IvParameterSpec(aesIv));

            byte[] plaintext = aesCipher.doFinal(ciphertext);

            int data_len = plaintext.length - 1 - plaintext[0];

            /* Copy only data */
            byte[] data = new byte[data_len];

            System.arraycopy(plaintext, 1, data, 0, data_len);

            // Log.d("BLEApp", "[AES] Successful encryption");

            return data;
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

    public byte[] encryptMessage(byte[] data)
    {
        /* Create padded buffer */
        int len = data.length + 1; /* + 1 for padding byte */
        byte pad = 0;

        if (len % AES_BLOCK_SIZE != 0)
            pad = (byte)(AES_BLOCK_SIZE - (len % AES_BLOCK_SIZE));

        byte[] plaintext = new byte[len + pad];

        plaintext[0] = pad;

        /* Copy buffer */
        System.arraycopy(data, 0, plaintext, 1, data.length);

        String strKey = "";
        for (int i = 0; i < aesKey.length; ++i)
            strKey += String.format("%x ", aesKey[i]);

        // Log.d("BLE", String.format("[CIPHER] AES Key: %s", strKey));

        String strIv = "";
        for (int i = 0; i < aesIv.length; ++i)
            strIv += String.format("%x ", aesIv[i]);

        // Log.d("BLE", String.format("[CIPHER] IV: %s", strIv));

        /* Encrypt */
        try {
            Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
            aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES256"), new IvParameterSpec(aesIv));

            byte[] ciphertext = aesCipher.doFinal(plaintext);

            String strCipher = "";

            for (int i = 0; i < ciphertext.length; ++i)
                strCipher +=  String.format("%x ", ciphertext[i]);

            // Log.d("BLE", String.format("[Cipher] Ciphertext: %s\r\n", strCipher));

            // Log.d("BLEApp", "[AES] Successful encryption");

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
}
