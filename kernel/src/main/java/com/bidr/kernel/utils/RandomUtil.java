/**
 * Project Name:SpringBootCommon
 * File Name:RandomString.java
 * Package Name:com.srct.service.utils.security
 * Date:Apr 28, 2018 3:57:56 PM
 * Copyright (c) 2018, ruopeng.sha All Rights Reserved.
 */
package com.bidr.kernel.utils;

import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

/**
 * Title :RandomString
 *
 * @author ruopeng.sha
 */
public class RandomUtil {

    private static final String SOURCES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";

    private static final String NUMBER = "1234567890";

    private static final char[] EVEN = {'0', '2', '4', '6', '8'};
    private static final char[] ODD = {'1', '3', '5', '7', '9'};
    private static final char[] CODE = {'2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
    private static final char[] LENGTH = {'7', '8', '9'};

    private RandomUtil() {
    }

    public static String getStringWithNumber(int length) {
        Random random = new SecureRandom();
        if (length < 1) {
            return "";
        } else if (length == 1) {
            return Character.toString(NUMBER.charAt(random.nextInt(NUMBER.length())));
        } else {
            StringBuilder str = new StringBuilder(getString(length - 1));
            str.insert(random.nextInt(length - 1), NUMBER.charAt(random.nextInt(NUMBER.length())));
            return str.toString();
        }
    }

    /**
     * Generate a random string.
     *
     * @param length the length of the generated string.
     * @return string
     */
    public static String getString(int length) {
        if (length < 1) {
            return "";
        } else {
            Random random = new SecureRandom();
            char[] text = new char[length];
            for (int i = 0; i < length; i++) {
                text[i] = SOURCES.charAt(random.nextInt(SOURCES.length()));
            }
            return new String(text);
        }
    }

    public static String getNumber() {
        String suffix = getUUIDNumber(5);
        String sdf = new SimpleDateFormat("yyMMddHHmmssSSS").format(new Date());
        return sdf + suffix;
    }

    public static String getUUIDNumber(int length) {
        int hashCodeV = UUID.randomUUID().toString().hashCode();
        int value = Math.abs(hashCodeV);
        if (length > 10) {
            String format = "%" + length + "d";
            return String.format(format, value);
        } else {
            return String.valueOf(value).substring(0, length);
        }
    }

    public static Integer getNumber(Integer start, Integer end) {
        Random random = new SecureRandom();
        return random.nextInt(end - start) + start;
    }

    public static String getUUID() {
        String uuid22 = Base64Util.encode(compress(UUID.randomUUID()));
        if (uuid22 == null) {
            return null;
        }
        String res = uuid22.substring(0, uuid22.length() - 2);
        return res;
    }

    private static byte[] compress(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];
        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) ((msb >>> 8 * (7 - i)) & 0xFF);
            buffer[i + 8] = (byte) ((lsb >>> 8 * (7 - i)) & 0xFF);
        }
        return buffer;
    }

    public static String randomPassword() {
        int length = Integer.valueOf(RandomStringUtils.random(1, LENGTH));
        return RandomStringUtils.random(length, CODE) + createRandomNum(1);
    }

    public static String createRandomNum(int size) {
        return RandomStringUtils.randomNumeric(size);
    }

    public static String createRandomEvenNum() {
        return RandomStringUtils.random(1, EVEN);
    }

    public static String createRandomOddNum() {
        return RandomStringUtils.random(1, ODD);
    }

    public static String createRandomLetter(int size) {
        return RandomStringUtils.randomAlphabetic(size);
    }
}
