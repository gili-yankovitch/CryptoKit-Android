package com.example.bleapp;

public class BLEFragment {
    public static final int MAX_FRAG_SIZE = 16;

    public int pkt_id;
    public int frag_id;
    public int total_len;
    public byte[] frag;

    public BLEFragment()
    {
        pkt_id = 0;
        frag_id = 0;
        total_len = 0;
        frag = new byte[MAX_FRAG_SIZE];
    }
}
