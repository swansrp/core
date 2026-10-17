package com.bidr.forge.datasource.service;

import com.bidr.kernel.utils.DesUtil;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Title: DataSourceCrypto
 * Description: 数据源密码加解密。复用 kernel 的 DesUtil（DES/ECB/PKCS7Padding + Base64），
 * 密钥种子取 my.project.name（如 pm-hse；DES 取前 8 字节，不足 8 位右侧补 '0'，跨实例一致）。
 * 落库前加密、建连接池/测试连接时解密；解密失败按历史明文兜底，兼容旧数据
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
public class DataSourceCrypto {

    @Value("${my.project.name:smart-query}")
    private String seed;

    /** 测试/手工指定密钥种子 */
    public void setSeed(String seed) {
        this.seed = seed;
    }

    /** 加密：空值原样返回 */
    public String encrypt(String plain) {
        if (FuncUtil.isEmpty(plain)) {
            return plain;
        }
        return DesUtil.encrypt(plain, key());
    }

    /** 解密：空值原样返回；解密失败视为历史明文密码原样返回（兼容升级前数据） */
    public String decrypt(String value) {
        if (FuncUtil.isEmpty(value)) {
            return value;
        }
        try {
            return DesUtil.decrypt(value, key());
        } catch (Exception e) {
            return value;
        }
    }

    /** 是否已是本密钥可解的密文（更新流程 merge 回旧密文时防二次加密） */
    public boolean isEncrypted(String value) {
        if (FuncUtil.isEmpty(value)) {
            return false;
        }
        try {
            DesUtil.decrypt(value, key());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String key() {
        String s = FuncUtil.isEmpty(seed) ? "smart-query" : seed;
        if (s.length() >= 8) {
            return s; // DES 只取密钥前 8 字节
        }
        // DES 要求密钥至少 8 字节，短种子右侧补 '0' 凑足，保证跨实例一致
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 8) {
            sb.append('0');
        }
        return sb.toString();
    }
}
