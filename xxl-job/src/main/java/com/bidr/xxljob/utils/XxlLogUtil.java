package com.bidr.xxljob.utils;

import com.xxl.job.core.context.XxlJobHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Title: XxlLogUtil
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/24 10:21
 */
public class XxlLogUtil {

    public static void log(String appendLogPattern, Object... appendLogArguments) {
        Logger logger = LoggerFactory.getLogger(getCallerClass());
        logger.info(String.format(loc(), appendLogPattern), appendLogArguments);
        XxlJobHelper.log(appendLogPattern, appendLogArguments);
    }

    private static Class<?> getCallerClass() {
        return new SecurityManager() {

            public Class<?> getClassName() {
                try {
                    return Class.forName(getClassContext()[3].getName());
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return null;
                }
            }
        }.getClassName();
    }

    private static String loc() {
        return "(" + getCallerClassName() + ".java:" + getCallerLine() + ") " + "%s";
    }

    private static String getCallerClassName() {
        return new SecurityManager() {

            public String getClassName() {
                return getClassContext()[4].getSimpleName();
            }
        }.getClassName();
    }

    private static int getCallerLine() {
        return new Throwable().getStackTrace()[3].getLineNumber();
    }
}
