package com.bidr.wechat.sdk;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;

import static com.bidr.kernel.utils.DesUtil.AES_ALG;


/**
 * Title: WXMiniDecrypt
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/10 21:56
 */
public class WxMiniDecrypt {

    public static String decrypt(String encryptedDataStr, String keyBytesStr, String ivStr) {
        byte[] result = null;
        byte[] encryptedData;
        byte[] sessionKey;
        byte[] iv;

        try {
            sessionKey = Base64.decodeBase64(keyBytesStr);
            encryptedData = Base64.decodeBase64(encryptedDataStr);
            iv = Base64.decodeBase64(ivStr);

            // 如果密钥不足16位，那么就补足. 这个if 中的内容很重要
            int base = 16;
            if (sessionKey.length % base != 0) {
                int groups = sessionKey.length / base + 1;
                byte[] temp = new byte[groups * base];
                Arrays.fill(temp, (byte) 0);
                System.arraycopy(sessionKey, 0, temp, 0, sessionKey.length);
                sessionKey = temp;
            }
            // 初始化
            Security.addProvider(new BouncyCastleProvider());
            // 转化成JAVA的密钥格式
            Key key = new SecretKeySpec(sessionKey, AES_ALG);
            Cipher cipher = null;
            try {
                // 初始化cipher
                cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                result = cipher.doFinal(encryptedData);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                e.printStackTrace();
            }
            if (result != null && result.length > 0) {
                return new String(result, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
