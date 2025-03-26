package com.bidr.authorization.config.log;

import org.slf4j.MDC;

import java.util.Map;

import static com.bidr.platform.config.log.LogMdcConstant.*;

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
        forkMdc(LOG_TOKEN, map);
        forkMdc(REQUEST_ID, map);
        forkMdc(URI, map);
        forkMdc(METHOD, map);
        forkMdc(STATUS, map);
        forkMdc(IP, map);
        forkMdc(TOTAL_TIME, map);
    }

    public static void forkMdc(String key, Map<String, String> map) {
        MDC.put(key, map.get(key));
    }

    public static void destroyMdc() {
        MDC.remove(LOG_TOKEN);
        MDC.remove(REQUEST_ID);
        MDC.remove(URI);
        MDC.remove(IP);
        MDC.remove(TOTAL_TIME);
        MDC.remove(METHOD);
        MDC.remove(STATUS);
    }
}
