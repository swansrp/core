package com.bidr.forge.datasource;

import com.bidr.forge.datasource.service.DataSourceCrypto;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: DataSourceCryptoTest
 * Description: 数据源密码加解密测试：密钥种子取 my.project.name 语义（此处以固定种子验证），
 * 覆盖往返一致、密文非明文、种子隔离、空值直通与历史明文兜底
 *
 * @author Sharp
 * @since 2026/8/18
 */
public class DataSourceCryptoTest {

    private DataSourceCrypto crypto(String seed) {
        DataSourceCrypto crypto = new DataSourceCrypto();
        crypto.setSeed(seed);
        return crypto;
    }

    @Test
    public void encryptDecryptRoundTrip() {
        DataSourceCrypto crypto = crypto("pm-hse");
        String plain = "P@ss#2026-数仓只读";
        String cipher = crypto.encrypt(plain);
        Assert.assertNotNull(cipher);
        Assert.assertNotEquals(plain, cipher);
        Assert.assertEquals(plain, crypto.decrypt(cipher));
    }

    @Test
    public void differentSeedCannotDecrypt() {
        DataSourceCrypto cryptoA = crypto("pm-hse");
        DataSourceCrypto cryptoB = crypto("other-project");
        String cipher = cryptoA.encrypt("secret-123");
        // 异种子解密失败走兜底：原样返回密文，绝不泄漏明文
        Assert.assertEquals(cipher, cryptoB.decrypt(cipher));
    }

    @Test
    public void emptyAndBlankPassThrough() {
        DataSourceCrypto crypto = crypto("pm-hse");
        Assert.assertNull(crypto.encrypt(null));
        Assert.assertEquals("", crypto.encrypt(""));
        Assert.assertNull(crypto.decrypt(null));
        Assert.assertEquals("", crypto.decrypt(""));
    }

    @Test
    public void legacyPlainTextPassThrough() {
        DataSourceCrypto crypto = crypto("pm-hse");
        // 升级前的明文密码：不是合法 Base64 密文，解密失败按原样返回，保证平滑兼容
        Assert.assertEquals("plain-pwd@123", crypto.decrypt("plain-pwd@123"));
    }
}
