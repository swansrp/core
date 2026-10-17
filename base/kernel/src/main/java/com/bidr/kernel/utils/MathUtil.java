package com.bidr.kernel.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Title: MathUtil
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 */

public class MathUtil {

    public static final double ZERO = 0.0D;
    public static final int SCALE_TWO = 2;
    public static final int SCALE_DEVIDE = 20;
    // 默认除法运算精度
    private static final int DEF_DIV_SCALE = 10;
    private static final int GREATER = 1;
    private static final int EQUAL = 0;
    private static final int LESS = -1;

    private MathUtil() {

    }

    /**
     * 提供精确的加法运算
     *
     * @param v1 被加数
     * @param v2 加数
     * @return 两个参数的和
     */
    public static double add(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(v1);
        BigDecimal b2 = new BigDecimal(v2);
        return b1.add(b2).doubleValue();
    }

    public static double add(double... d) {
        return addAsBigDecimal(d).doubleValue();
    }

    public static BigDecimal addAsBigDecimal(double... d) {
        BigDecimal result = BigDecimal.ZERO;
        for (double e : d) {
            result = result.add(convert(e));
        }
        return result;
    }

    public static BigDecimal convert(Double d) {
        if (d == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(d);
    }

    /**
     * 提供（相对）精确的除法运算，当发生除不尽的情况时，精确到 小数点以后10位，以后的数字四舍五入
     *
     * @param v1 被除数
     * @param v2 除数
     * @return 两个参数的商
     */
    public static double div(double v1, double v2) {
        return div(v1, v2, DEF_DIV_SCALE);
    }

    /**
     * 提供（相对）精确的除法运算。当发生除不尽的情况时，由scale参数指 定精度，以后的数字四舍五入。
     *
     * @param v1    被除数
     * @param v2    除数
     * @param scale 表示表示需要精确到小数点以后几位。
     * @return 两个参数的商
     */
    public static double div(double v1, double v2, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        BigDecimal b1 = new BigDecimal(v1);
        BigDecimal b2 = new BigDecimal(v2);
        return b1.divide(b2, scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double divide(double d1, double d2) {
        return divideAsBigDecimal(d1, d2).doubleValue();
    }

    public static BigDecimal divideAsBigDecimal(double d1, double d2) {
        BigDecimal bd1 = convert(d1);
        BigDecimal bd2 = convert(d2);
        return bd1.divide(bd2, SCALE_DEVIDE, RoundingMode.HALF_UP);
    }

    /**
     * 获取小数位数
     *
     * @param d
     * @return
     */
    public static int getScale(Double d) {
        BigDecimal decimal = convert(d);
        return decimal.scale();
    }

    public static boolean isEqual(Double d1, Double d2) {
        return compare(d1, d2) == EQUAL;
    }

    // ----------------------------------------------

    /**
     * 将两个double转换为BigDecimal比较大小
     *
     * @param d1
     * @param d2
     * @return -1, 0, or 1 分别表示 小于, 等于, or 大于
     */
    public static int compare(Double d1, Double d2) {
        BigDecimal bd1 = convert(d1);
        BigDecimal bd2 = convert(d2);
        return bd1.compareTo(bd2);
    }

    public static boolean isGreaterOrEqual(Double d1, Double d2) {
        return !isLess(d1, d2);
    }

    public static boolean isLess(Double d1, Double d2) {
        return compare(d1, d2) == LESS;
    }

    public static boolean isLessOrEqual(Double d1, Double d2) {
        return !isGreater(d1, d2);
    }

    // ----------------------------------------------

    public static boolean isGreater(Double d1, Double d2) {
        return compare(d1, d2) == GREATER;
    }

    /**
     * 提供精确的乘法运算
     *
     * @param v1 被乘数
     * @param v2 乘数
     * @return 两个参数的积
     */
    public static double mul(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(v1);
        BigDecimal b2 = new BigDecimal(v2);
        return b1.multiply(b2).doubleValue();
    }

    public static double multiply(double... d) {
        return multiplyAsBigDecimal(d).doubleValue();
    }

    public static BigDecimal multiplyAsBigDecimal(double... d) {
        BigDecimal result = BigDecimal.ONE;
        for (double e : d) {
            result = result.multiply(convert(e));
        }
        return result;
    }

    // ----------------------------------------------

    /**
     * 提供精确的小数位四舍五入处理。
     *
     * @param v     需要四舍五入的数字
     * @param scale 小数点后保留几位
     * @return 四舍五入后的结果
     */
    public static double round(double v, int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("The scale must be a positive integer or zero");
        }
        BigDecimal b = new BigDecimal(v);
        BigDecimal one = new BigDecimal("1");
        return b.divide(one, scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static Double round(Double d, int scale) {
        return convert(d).setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 提供精确的减法运算
     *
     * @param v1 被减数
     * @param v2 减数
     * @return 两个参数的差
     */
    public static double sub(double v1, double v2) {
        BigDecimal b1 = new BigDecimal(v1);
        BigDecimal b2 = new BigDecimal(v2);
        return b1.subtract(b2).doubleValue();
    }

    /**
     * 获得两个double相减的结果
     *
     * @param d1
     * @param d2
     * @return
     */
    public static double substract(double d1, double d2) {
        return subtractAsBigDecimal(d1, d2).doubleValue();
    }

    public static BigDecimal subtractAsBigDecimal(double d1, double d2) {
        BigDecimal bd1 = convert(d1);
        BigDecimal bd2 = convert(d2);
        return bd1.subtract(bd2);
    }
}
