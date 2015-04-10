package com.threeDBJ.bluetooth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class BluetoothMessage {
    public int cmd, arg=-1;
    public byte[] bytes;

    public BluetoothMessage() {}

    public BluetoothMessage(int cmd) {
        this.cmd = cmd;
    }

    public BluetoothMessage(int cmd, byte[] bytes) {
        this(cmd);
        this.bytes = bytes;
    }

    public BluetoothMessage(int cmd, String msg) {
        this(cmd);
        try {
            this.bytes = msg.getBytes("UTF-8");
        } catch(UnsupportedEncodingException e) {
            // UTF-8 support check at startup
        }
    }

    public BluetoothMessage(int cmd, int arg, byte[] bytes) {
        this(cmd, bytes);
        this.arg = arg;
    }

    public boolean hasArg() {
        return arg != -1;
    }

    public void write(OutputStream out) throws IOException {
        byte len = 2;
        if(bytes != null) len += bytes.length;
        byte[] msg = new byte[len];
        msg[0] = len;
        msg[1] = (byte)cmd;
        if(bytes != null) System.arraycopy(bytes, 0, msg, 2, bytes.length);
        out.write(msg);
        if(hasArg()) {
            DebugLog.e("BTAndroid", "BT MESSAGE ARG DEPRECATED");
        }
    }
}
