package com.example.bleapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

public class BLEFragmentComm {
    private final int MAX_FRAGMENTS = 16;
    private final int FRAGMENT_PKT_ID_SIZE = 1;
    private final int FRAGMENT_FRAG_ID_SIZE = 1;
    private final int FRAGMENT_TOT_LEN_SIZE = 1;
    private final int FRAGMENT_SIZE = FRAGMENT_PKT_ID_SIZE + FRAGMENT_FRAG_ID_SIZE + FRAGMENT_TOT_LEN_SIZE + BLEFragment.MAX_FRAG_SIZE;

    private final int FRAGMENT_OFFSET_PKT_ID = 0;
    private final int FRAGMENT_OFFSET_FRAG_ID = FRAGMENT_OFFSET_PKT_ID + FRAGMENT_PKT_ID_SIZE;
    private final int FRAGMENT_OFFSET_TOT_LEN = FRAGMENT_OFFSET_FRAG_ID + FRAGMENT_FRAG_ID_SIZE;
    private final int FRAGMENT_OFFSET_DATA = FRAGMENT_OFFSET_TOT_LEN + FRAGMENT_TOT_LEN_SIZE;


    private static BLEFragmentComm _instance = null;

    private int pkt_ids;
    private BLEFragment[] in_frags;

    private int in_pkt_id;
    private int recvd_len;
    private int total_len;
    private byte[] packet;

    /* This is the lowest layer. Next one is only the Bluetooth stack */
    private BluetoothGatt bleGattConn;
    private BluetoothGattCharacteristic bleGattTxCharacteristic;

    private BLEFragmentComm()
    {
        pkt_ids = 0;
        in_frags = new BLEFragment[MAX_FRAGMENTS];

        for (int i = 0; i < MAX_FRAGMENTS; ++i)
        {
            in_frags[i] = new BLEFragment();
        }

        in_pkt_id = 0;
        recvd_len = 0;
        total_len = 0;
        packet = new byte[MAX_FRAGMENTS * BLEFragment.MAX_FRAG_SIZE];

        bleGattConn = null;
        bleGattTxCharacteristic = null;
    }

    public void reset()
    {
        pkt_ids = 0;
        in_frags = new BLEFragment[MAX_FRAGMENTS];

        for (int i = 0; i < MAX_FRAGMENTS; ++i)
        {
            in_frags[i] = new BLEFragment();
        }

        in_pkt_id = 0;
        recvd_len = 0;
        total_len = 0;
        packet = new byte[MAX_FRAGMENTS * BLEFragment.MAX_FRAG_SIZE];

        if (bleGattConn != null)
            bleGattConn.disconnect();

        bleGattConn = null;
        bleGattTxCharacteristic = null;
    }

    public static BLEFragmentComm getInstance()
    {
        if (_instance == null)
        {
            _instance = new BLEFragmentComm();
        }

        return _instance;
    }

    public void setupConnection(BluetoothGatt g, BluetoothGattCharacteristic c)
    {
        bleGattConn = g;
        bleGattTxCharacteristic = c;
    }

    public byte[] ReassembleFragment(byte[] data)
    {
        int len = data.length;
        int pkt_id = data[FRAGMENT_OFFSET_PKT_ID];
        int frag_id = data[FRAGMENT_OFFSET_FRAG_ID];
        int tot_len = data[FRAGMENT_OFFSET_TOT_LEN];
        int data_size = BLEFragment.MAX_FRAG_SIZE;

        /* Error */
        if (frag_id >= MAX_FRAGMENTS) {
            Log.d("CryptoKit", String.format("[FRAG] Invalid fragment id %d", frag_id));
            return null;
        }

        /* Error */
        if (len < FRAGMENT_SIZE) {
            Log.d("CryptoKit", String.format("[FRAG] Invalid fragment size %d", len));
            return null;
        }

        /* Reset on a new packet */
        if (in_pkt_id != pkt_id)
        {

            for (int i = 0; i < MAX_FRAGMENTS; ++i)
            {
                for (int j = 0; j < BLEFragment.MAX_FRAG_SIZE; ++j)
                {
                    in_frags[i].pkt_id = 0;
                    in_frags[i].frag_id = 0;
                    in_frags[i].total_len = 0;
                    in_frags[i].frag[j] = 0;
                }
            }

            for (int i = 0; i < MAX_FRAGMENTS * BLEFragment.MAX_FRAG_SIZE; ++i)
                packet[i] = 0;

            recvd_len = 0;
            in_pkt_id = pkt_id;
            total_len = tot_len;
        }

        int total_fragments = total_len / BLEFragment.MAX_FRAG_SIZE;

        /* Trailing fragment */
        if (total_len % BLEFragment.MAX_FRAG_SIZE != 0)
            total_fragments += 1;

        /* Error */
        if (frag_id >= total_fragments) {
            Log.d("CryptoKit", String.format("[FRAG] Invalid fragment id %d, total: %d", frag_id, total_fragments));
            return null;
        }

        /* Error - no fragment rewrites */
        if (in_frags[frag_id].pkt_id == pkt_id) {
            Log.d("CryptoKit", String.format("[FRAG] Trying to rewrite fragment ID: %d", frag_id));
            return null;
        }

        if ((total_len % BLEFragment.MAX_FRAG_SIZE != 0) && (frag_id + 1 == total_fragments)) {
            data_size = total_len % BLEFragment.MAX_FRAG_SIZE;
        }

        Log.d("CryptoKit", String.format("[FRAG] Fragment Id: %d Total fragments: %d Frag size: %d Total size: %d", frag_id, total_fragments, data_size, total_len));

        in_frags[frag_id].pkt_id = pkt_id;
        in_frags[frag_id].frag_id = frag_id;
        in_frags[frag_id].total_len = tot_len;

        System.arraycopy(data, FRAGMENT_OFFSET_DATA, in_frags[frag_id].frag, 0, data_size);
        /*
        for (int i = 0; i < data_size; ++i)
            in_frags[frag_id].frag[i] = data[FRAGMENT_OFFSET_DATA + i];
         */

        recvd_len += data_size;

        /* Not finished yet */
        if (recvd_len != total_len) {
            Log.d("CryptoKit", String.format("[FRAG] Packet not finished. Total: %d Recvd: %d", total_len, recvd_len));
            return null;
        }

        for (int i = 0; i < total_fragments; ++i)
        {
            data_size = BLEFragment.MAX_FRAG_SIZE;

            if ((total_len % BLEFragment.MAX_FRAG_SIZE != 0) && (i + 1 == total_fragments))
                data_size = total_len % BLEFragment.MAX_FRAG_SIZE;

            /* Copy packet data */
            System.arraycopy(in_frags[i].frag, 0, packet, i * BLEFragment.MAX_FRAG_SIZE, data_size);
            /*
            for (int j = 0; j < data_size; ++j)
                packet[i * BLEFragment.MAX_FRAG_SIZE + j] = in_frags[i].frag[j];

             */
        }

        return packet;
    }

    public byte[][] FragmentData(final byte[] data)
    {
        int len = data.length;
        int id = ++pkt_ids;
        int total_fragments = len / BLEFragment.MAX_FRAG_SIZE;

        /* Trailing fragment */
        if (len % BLEFragment.MAX_FRAG_SIZE != 0)
            total_fragments += 1;

        byte[][] fragments = new byte[total_fragments][FRAGMENT_SIZE];

        for (int i = 0; i < total_fragments; ++i) {
            int data_size = BLEFragment.MAX_FRAG_SIZE;

            if ((len % BLEFragment.MAX_FRAG_SIZE != 0) && (i + 1 == total_fragments))
                data_size = len % BLEFragment.MAX_FRAG_SIZE;

            fragments[i][FRAGMENT_OFFSET_PKT_ID] = (byte) id;
            fragments[i][FRAGMENT_OFFSET_FRAG_ID] = (byte) i;
            fragments[i][FRAGMENT_OFFSET_TOT_LEN] = (byte) len;

            /* Copy data */
            System.arraycopy(data, i * BLEFragment.MAX_FRAG_SIZE, fragments[i], FRAGMENT_OFFSET_DATA, data_size);
            /*
            for (int j = 0; j < data_size; ++j) {
                fragments[i][FRAGMENT_OFFSET_DATA + j] = data[(i * BLEFragment.MAX_FRAG_SIZE) + j];
            }
            */
            Log.d("CryptoKit", String.format("[FRAG] Sending Packet Id: %d Fragment Id: %d Total Size: %d Buffer size: %d", id, i, len, fragments[i].length));
        }

        return fragments;
    }
}
