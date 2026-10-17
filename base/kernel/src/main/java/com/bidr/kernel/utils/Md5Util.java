package com.bidr.kernel.utils;

import org.bouncycastle.util.encoders.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Title: Md5Util
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 12:46
 */
public class Md5Util {

    private Md5Util() {
    }

    /**
     * common MD5
     *
     * @return MD5
     * @author daniel Date: Apr 28, 2018 5:38:54 PM
     * @author ruopeng.sha
     */
    public static String MD5(String input) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return "check jdk";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        char[] charArray = input.toCharArray();
        byte[] byteArray = new byte[charArray.length];
        for (int i = 0; i < charArray.length; i++) {
            byteArray[i] = (byte) charArray[i];
        }
        byte[] md5Bytes = md5.digest(byteArray);
        StringBuilder hexValue = new StringBuilder();
        for (byte md5Byte : md5Bytes) {
            int val = ((int) md5Byte) & 0xff;
            if (val < 16) {
                hexValue.append("0");
            }
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();
    }

    /**
     * salt MD5 Date: Apr 28, 2018 5:38:54 PM
     *
     * @param str
     * @return salt md5
     * @author ruopeng.sha
     */
    public static String generate(String str) {
        if (str == null) {
            return "";
        } else {
            Random r = new Random();
            StringBuilder sb = new StringBuilder(16);
            sb.append(r.nextInt(99999999)).append(r.nextInt(99999999));
            int len = sb.length();
            if (len < 16) {
                for (int i = 0; i < 16 - len; i++) {
                    sb.append("0");
                }
            }
            String salt = sb.toString();
            str = md5Hex(str + salt);
            if (str == null) {
                return "";
            }
            char[] cs = new char[48];
            for (int i = 0; i < 48; i += 3) {
                cs[i] = str.charAt(i / 3 * 2);
                char c = salt.charAt(i / 3);
                cs[i + 1] = c;
                cs[i + 2] = str.charAt(i / 3 * 2 + 1);
            }
            return new String(cs);
        }
    }

    private static String md5Hex(String src) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bs = md5.digest(src.getBytes());
            return new String(Hex.encode(bs));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Date: Apr 28, 2018 5:38:54 PM
     *
     * @param str
     * @param md5
     * @return result
     * @author ruopeng.sha
     */
    public static boolean verify(String str, String md5) {
        char[] cs1 = new char[32];
        char[] cs2 = new char[16];
        for (int i = 0; i < 48; i += 3) {
            cs1[i / 3 * 2] = md5.charAt(i);
            cs1[i / 3 * 2 + 1] = md5.charAt(i + 2);
            cs2[i / 3] = md5.charAt(i + 1);
        }
        String salt = new String(cs2);
        String passwordWithSaltMD5 = md5Hex(str + salt);
        if (passwordWithSaltMD5 != null) {
            return passwordWithSaltMD5.equals(new String(cs1));
        }
        return false;
    }
}
