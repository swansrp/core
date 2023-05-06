package com.bidr.kernel.utils;

import com.bidr.kernel.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Title: DesUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/10/9 9:51
 */
@Slf4j
public class DesUtil {

    public static final String CHARSET_UTF8 = "UTF-8";
    public static final String CHARSET_GBK = "GBK";
    public static final String AES_ALG = "AES";
    public static final String RSA_ALG = "RSA";
    public static final String SIGN_TYPE_RSA = "RSA";
    public static final String SIGN_TYPE_RSA2 = "RSA2";
    public static final String SIGN_ALGORITHMS = "SHA1WithRSA";
    public static final String SIGN_SHA256RSA_ALGORITHMS = "SHA256WithRSA";

    static {
        // add BC provider
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String encrypt(String encryptText, String key) {
        return encrypt(encryptText, key, CHARSET_UTF8);
    }

    /**
     * 加密
     *
     * @param encryptText 需要加密的信息
     * @param key         加密密钥
     * @return 加密后Base64编码的字符串
     */
    public static String encrypt(String encryptText, String key, String charset) {

        if (encryptText == null || key == null) {
            throw new IllegalArgumentException("encryptText or key must not be null");
        }

        try {
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes());
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);

            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS7Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] bytes = cipher.doFinal(encryptText.getBytes(Charset.forName(charset)));
            return Base64.getEncoder().encodeToString(bytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException | NoSuchPaddingException
                 | BadPaddingException | NoSuchProviderException | IllegalBlockSizeException e) {
            log.error("encrypt", e);
            throw new ServiceException("encrypt failed");
        }

    }

    public static String decrypt(String encryptText, String key) {
        return decrypt(encryptText, key, CHARSET_UTF8);
    }

    /**
     * 解密
     *
     * @param decryptText 需要解密的信息
     * @param key         解密密钥，经过Base64编码
     * @return 解密后的字符串
     */
    public static String decrypt(String decryptText, String key, String charset) {

        if (decryptText == null || key == null) {
            throw new IllegalArgumentException("decryptText or key must not be null");
        }

        try {
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes());
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);

            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS7Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] bytes = cipher.doFinal(Base64.getDecoder().decode(decryptText));
            return new String(bytes, Charset.forName(charset));

        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException | NoSuchPaddingException
                 | BadPaddingException | NoSuchProviderException | IllegalBlockSizeException e) {
            throw new ServiceException("decrypt failed");
        }
    }
}
