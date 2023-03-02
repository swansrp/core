package com.bidr.kernel.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Title: NetUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/1/29 18:11
 */
@Slf4j
public class NetUtil {

    public static String getLocalIp() {
        return formatIp(getLocalAddress());
    }

    private static String formatIp(InetAddress inetAddress) {
        if (inetAddress == null) {
            return "";
        }
        return StringUtils.defaultIfEmpty(inetAddress.getHostAddress(), "");
    }

    private static InetAddress getLocalAddress() {
        try {
            InetAddress candidateAddress = null;
            for (Enumeration<NetworkInterface> iFaceIterator = NetworkInterface.getNetworkInterfaces(); iFaceIterator
                    .hasMoreElements(); ) {
                NetworkInterface iFace = iFaceIterator.nextElement();
                for (Enumeration<InetAddress> addressIterator = iFace.getInetAddresses(); addressIterator
                        .hasMoreElements(); ) {
                    InetAddress iNetAddr = addressIterator.nextElement();
                    if (iNetAddr.isLoopbackAddress()) {
                        continue;
                    }
                    if (iNetAddr.isSiteLocalAddress()) {
                        return iNetAddr;
                    }
                    if (candidateAddress == null) {
                        candidateAddress = iNetAddr;
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            return InetAddress.getLocalHost();
        } catch (Exception e) {
            log.info("获取本地ip地址出错", e);
        }
        return null;
    }

    public static String getHostName() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName();
        } catch (Exception e) {
            log.info("获取本地主机名出错", e);
            return "";
        }
    }
}
