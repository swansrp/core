/**
 * Title: DateUtils.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.utils
 * @author sharuopeng
 * @since 2019-03-13 09:33:05
 */
package com.bidr.kernel.utils;

import com.bidr.kernel.exception.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * @author sharuopeng
 */

public class DateUtil {
    public static final String DATE = "yyyyMMdd";
    public static final String MONTH = "yyyyMM";
    public static final String DATE_DOT = "yyyy.MM.dd";

    public static final String TIME = "HHmmss";
    public static final String DATE_TIME = "yyyyMMddHHmmss";
    public static final String DATE_NORMAL = "yyyy-MM-dd";
    public static final String TIME_NORMAL = "HH:mm:ss";
    public static final String DATE_TIME_NORMAL = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_TIME_T = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String DATE_SLANT = "yyyy/MM/dd";
    public static final String DATE_DEFAULT = "EEE MMM dd HH:mm:ss zzz yyyy";
    public static final int QUANTUM_WEEK_DAY = 7;
    public static final int QUANTUM_DAY_HOUR = 24;
    public static final int QUANTUM_HOUR_MINUTE = 60;
    public static final int QUANTUM_MINUTE_SECOND = 60;
    public static final int QUANTUM_SECOND_MILLIS = 1000;
    public static final String TEXT_HOUR_UNIT = "小时";
    public static final String TEXT_MINUTE_UNIT = "分钟";
    public static final String TEXT_SECOND_UNIT = "秒";
    public static final String PATTERN_DATE_NORMAL = "^[1-9]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$";
    public static final String PATTERN_TIME_NORMAL = "^(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$";
    public static final String PATTERN_DATE_TIME_NORMAL = "^[1-9]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])\\s+(20|21|22|23|[0-1]\\d):[0-5]\\d:[0-5]\\d$";
    private static final int MILL_SECOND_PER_SECOND = 1000;

    /**
     * String类型date增加指定天数:返回Date类型
     *
     * @param strDate
     * @param formatStr
     * @param day
     * @return
     */
    public static Date addDate(String strDate, String formatStr, int day) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(formatStr);
            Date date = format.parse(strDate);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.DAY_OF_YEAR, day);
            return calendar.getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Date getDate(Date date, int defaultType, int defaultValue) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(defaultType, c.get(defaultType) + defaultValue);
        return c.getTime();
    }

    public static Date yesterdayBeginTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date yesterdayEndTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    public static Date todayBeginTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date todayEndTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    public static Date tomorrowBeginTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date tomorrowEndTime() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    public static Date yearBeginTime(Integer year) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date yearEndTime(Integer year) {
        return DateUtil.addMilliSeconds(yearBeginTime(year + 1), -1);
    }

    public static Date monthBeginTime(Integer year, Integer month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date monthEndTime(Integer year, Integer month) {
        return DateUtil.addMilliSeconds(monthBeginTime(year, month + 1), -1);
    }

    public static Date beginTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date endTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    public static int currentYear() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR);
    }

    public static int currentMonth() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.MONTH);
    }

    public static int currentYearWeek() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.WEEK_OF_YEAR);
    }

    public static Date tomorrow(Date date, int defaultType, int defaultValue) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(defaultType, c.get(defaultType) + defaultValue);
        return c.getTime();
    }

    public static Date addMonth(Date date, int month) {
        return org.apache.commons.lang3.time.DateUtils.addMonths(date, month);
    }

    public static Date addSeconds(Date date, int seconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.SECOND, seconds);
        return calendar.getTime();
    }

    public static Date addMilliSeconds(Date date, int millisecond) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MILLISECOND, millisecond);
        return calendar.getTime();
    }

    public static long secondDiff(Date laterTime, Date earlyTime) {
        long millSecondDiff = laterTime.getTime() - earlyTime.getTime();
        return millSecondDiff / MILL_SECOND_PER_SECOND;
    }

    public static long dayDiff(String laterDateStr, String earlyDateStr) {
        return dayDiff(stringToDate(laterDateStr), stringToDate(earlyDateStr));
    }

    public static long dayDiff(Date laterDate, Date earlyDate) {
        LocalDate localLaterDate = dateToLocalDate(laterDate);
        LocalDate localEarlyDate = dateToLocalDate(earlyDate);
        return localLaterDate.toEpochDay() - localEarlyDate.toEpochDay();
    }

    public static Date stringToDate(String dateTime) {
        if (dateTime == null || "".equals(dateTime)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyyMMdd").parse(dateTime);
        } catch (ParseException e) {
            throw new ServiceException(dateTime, e);
        }
    }

    public static LocalDate dateToLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static String getNextDate(String dateString) {
        Date date = stringToDate(dateString);
        Date nextDate = addDate(date, 1);
        return dateToString(nextDate);
    }

    /**
     * 增加指定天数:返回Date类型
     *
     * @param day
     * @return
     */
    public static Date addDate(Date date, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, day);
        return calendar.getTime();
    }

    public static String dateToString(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyyMMdd").format(date);
    }

    /**
     * String类型date返回Date类型
     *
     * @param strDate
     * @param formatStr
     * @return
     */
    public static Date formatDate(String strDate, String formatStr) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(formatStr);
            Date date = format.parse(strDate);
            return date;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Date stringToDateTime(String dateTime, String dateFormat) {
        if (dateTime == null || "".equals(dateTime)) {
            return null;
        }
        try {
            return new SimpleDateFormat(dateFormat).parse(dateTime);
        } catch (ParseException e) {
            throw new ServiceException(dateTime, e);
        }
    }

    public static Date stringToDateFormat(String dateTime) {
        if (dateTime == null || "".equals(dateTime)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateTime);
        } catch (ParseException e) {
            throw new ServiceException(dateTime, e);
        }
    }

    public static String dateTimeToString(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyyMMddHHmmss").format(date);
    }

    public static String dateTimeToBETSString(Date date) {
        if (date == null) {
            return null;
        }
        String dateS = new SimpleDateFormat("yyyy-MM-dd").format(date);
        String timeS = new SimpleDateFormat("HH:mm:ss").format(date);
        return dateS + "T" + timeS;
    }

    public static String timeToString(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("HHmmss").format(date);
    }

    public static String dateTimeExtToString(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyyMMddHHmmssSSS").format(date);
    }

    public static String timeExtToString(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("HHmmssSSS").format(date);
    }

    public static String getYearAndMonth(String dateStr) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMM");
        return format.format(stringToDate(dateStr));
    }

    public static String getDay(String dateStr) {
        SimpleDateFormat format = new SimpleDateFormat("dd");
        return format.format(stringToDate(dateStr));
    }

    public static Date combineStringToDate(String date, String time) {
        if (!StringUtils.isEmpty(date) && !StringUtils.isEmpty(time)) {
            return stringToDateTime(date + time);
        } else {
            return null;
        }
    }

    public static Date stringToDateTime(String dateTime) {
        if (dateTime == null || "".equals(dateTime)) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss").parse(dateTime);
        } catch (ParseException e) {
            throw new ServiceException(dateTime, e);
        }
    }

    public static String formatDefaultDateString(String s, String patten) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_DEFAULT, Locale.US);
        Date date = null;
        try {
            date = sdf.parse(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return formatDate(date, patten);
    }

    public static String formatDate(Date date, String formatStr) {
        if (date == null) {
            return "";
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat(formatStr);
            return format.format(date);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 当前时间按照格式转换
     *
     * @param format
     * @return
     */
    public static String getFormatTime(String format) {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    /**
     * 日期加上天数返回加上后的天数
     */
    public static String addDate(String date, int day) {
        if (day == 0 && date.isEmpty()) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Date currdate = null;
        try {
            currdate = format.parse(date);
            return format.format(DateUtils.addDays(currdate, day));
        } catch (ParseException e) {
            throw new ServiceException(date, e);
        }
    }
}
