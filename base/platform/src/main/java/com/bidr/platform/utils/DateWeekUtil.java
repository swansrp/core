package com.bidr.platform.utils;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.bidr.platform.vo.week.VersionConvertRes;
import com.bidr.platform.vo.week.VersionDateConvertRes;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;

/**
 * Title: DateWeekUtil
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/06 13:35
 */
@Slf4j
public class DateWeekUtil {

    private static final String VERSION_FORMAT = "%s-%s";

    public static void main(String[] args) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, 11, 31);
        log.info(getVersion(calendar.getTime()).toString());
    }

    public static Integer getVersion(Date date) {
        DateTime dateTime = new DateTime(date);
        int week = dateTime.getField(DateField.WEEK_OF_YEAR);
        int year = dateTime.getField(DateField.YEAR);
        return year * 100 + week;
    }

    public static Integer getNextVersion(Date date) {
        Integer version = getVersion(date);
        return getOffsetVersion(version, 1);
    }

    public static Integer getOffsetVersion(Integer version, Integer offset) {
        DateTime date = new DateTime();
        date.setField(DateField.YEAR, version / 100);
        date.setField(DateField.WEEK_OF_YEAR, version % 100);
        DateTime offsetDate = date.offset(DateField.WEEK_OF_YEAR, offset);
        int week = offsetDate.getField(DateField.WEEK_OF_YEAR);
        int year = offsetDate.getField(DateField.YEAR);
        return year * 100 + week;
    }

    public static Integer getPreviousVersion(Date date) {
        Integer version = getVersion(date);
        return getOffsetVersion(version, -1);
    }

    public static Integer getNextVersion(Integer version) {
        return getOffsetVersion(version, 1);
    }

    public static Integer getPreviousVersion(Integer version) {
        return getOffsetVersion(version, -1);
    }

    public static VersionConvertRes convertVersion(String version) {
        String year = version.split("-")[0];
        String week = version.split("-")[1];
        DateTime date = new DateTime();
        date.setField(DateField.YEAR, Integer.parseInt(year));
        date.setField(DateField.WEEK_OF_YEAR, Integer.parseInt(week));
        DateTime startAt = DateUtil.beginOfWeek(date);
        DateTime end = DateUtil.endOfWeek(date);
        return new VersionConvertRes(startAt, end);
    }

    public static VersionDateConvertRes convertVersion(Date date) {
        DateTime dateTime = new DateTime(date);
        int week = dateTime.getField(DateField.WEEK_OF_YEAR);
        int year = dateTime.getField(DateField.YEAR);
        return new VersionDateConvertRes(String.format(VERSION_FORMAT, year, week));
    }
}
