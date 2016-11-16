package com.limpoxe.zipsplitter;

import java.io.IOException;

/**
 * Created by cailiming on 16/11/7.
 */

public class BytesUtil {

    /**
     * 低位优先
     */
    public static short bytesToShot(byte[] src) {
        short value;
        value = (short) (((src[0] & 0xFF)
                |(src[1] & 0xFF)<<8));
        return value;
    }

    /**
     * 低位优先
     */
    public static int bytesToInt(byte[] src) {
        int value;
        value = (int) ((src[0] & 0xFF)
                | ((src[1] & 0xFF)<<8)
                | ((src[2] & 0xFF)<<16)
                | ((src[3] & 0xFF)<<24));
        return value;
    }

    /**
     * 低位优先
     */
    public static byte[] intToBytes(long value) {
        byte[] src = new byte[4];
        src[0] =  (byte) (value & 0xFF);
        src[1] =  (byte) ((value>>8) & 0xFF);
        src[2] =  (byte) ((value>>16) & 0xFF);
        src[3] =  (byte) ((value>>24) & 0xFF);
        return src;
    }

    /**
     * 低位优先
     */
    public static byte[] shotToBytes(int value) {
        byte[] src = new byte[2];
        src[0] =  (byte) (value & 0xFF);
        src[1] =  (byte) ((value>>8) & 0xFF);
        return src;
    }

    public static long revertInt(int intValue) throws IOException {
        return ((long)intValue) & 0xffffffffL;
    }

    public static int revertShot(short shortValue) throws IOException {
        return shortValue & 0xffff;
    }

    public static boolean containsNulByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

}
