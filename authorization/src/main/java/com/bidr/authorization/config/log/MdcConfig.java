package com.bidr.authorization.config.log;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Title: MdcConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/08 11:29
 */
public class MdcConfig {

    public static void forkLogInfo() {
        forkLogInfo(MDC.getCopyOfContextMap());
    }

    public static void forkLogInfo(Map<String, String> map) {
        forkMdc("logToken", map);
        forkMdc("REQUEST_ID", map);
        forkMdc("URI", map);
        forkMdc("method", map);
        forkMdc("status", map);
        forkMdc("IP", map);
    }

    public static void forkMdc(String key, Map<String, String> map) {
        MDC.put(key, map.get(key));
    }

    public static void destroyMdc() {
        MDC.remove("logToken");
        MDC.remove("reqToken");
        MDC.remove("URI");
        MDC.remove("IP");
        MDC.remove("totalTime");
        MDC.remove("method");
        MDC.remove("status");
    }
}
