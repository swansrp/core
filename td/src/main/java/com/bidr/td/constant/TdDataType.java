package com.bidr.td.constant;

public enum TdDataType {
    TIMESTAMP, INT, BIGINT, FLOAT, DOUBLE, BINARY, NCHAR, BOOL, TINYINT, SMALLINT;

    public String toSql() {
        switch (this) {
            case TIMESTAMP: return "TIMESTAMP";
            case INT: return "INT";
            case BIGINT: return "BIGINT";
            case FLOAT: return "FLOAT";
            case DOUBLE: return "DOUBLE";
            case BINARY: return "BINARY";
            case NCHAR: return "NCHAR";
            case BOOL: return "BOOL";
            case TINYINT: return "TINYINT";
            case SMALLINT: return "SMALLINT";
            default: throw new IllegalStateException("Unknown TdDataType: " + this);
        }
    }
}
