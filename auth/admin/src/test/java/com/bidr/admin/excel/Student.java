package com.bidr.admin.excel;

import lombok.Data;

/**
 * Title: Student
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/26 09:11
 */
@Data
public class Student {
    private String name;

    public Student(String name) {
        this.name = name;
    }
}
