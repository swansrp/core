package com.bidr.kernel.constant.db;

/**
 * Title: SqlConstant
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 14:40
 */
public class SqlConstant {
    public static final String INVALID = "0";
    public static final String VALID = "1";
    public static final String IGNORE_VALID = "-1";

    public static final String ID_FIELD = "id";
    public static final String CREATE_FIELD = "createAt";
    public static final String UPDATE_FIELD = "updateAt";
    public static final String VALID_FIELD = "valid";

    public static final int EQUAL = 0;
    public static final int NOT_EQUAL = 1;
    public static final int GREATER = 2;
    public static final int GREATER_EQUAL = 3;
    public static final int LESS = 4;
    public static final int LESS_EQUAL = 5;
    public static final int NULL = 6;
    public static final int NOT_NULL = 7;
    public static final int LIKE = 8;
    public static final int NOT_LIKE = 9;
    public static final int IN = 10;
    public static final int NOT_IN = 11;
    public static final int NOT_EQUAL_OR_NULL = 12;
    public static final int BETWEEN = 13;
    public static final int NOT_BETWEEN = 14;
    public static final int BETWEEN_OR_EQUAL = 15;
    public static final int NOT_BETWEEN_OR_EQUAL = 16;
}
