package com.bidr.admin.excel;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: Teacher
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/26 09:11
 */
@Data
public class Teacher {
    private final List<Student> studentList;
    private String name;

    public Teacher(String name) {
        this.name = name;
        studentList = new ArrayList<>();
    }
}
