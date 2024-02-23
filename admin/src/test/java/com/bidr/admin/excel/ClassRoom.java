package com.bidr.admin.excel;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ClassRoom
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/26 09:11
 */
@Data
public class ClassRoom {
    private String name;
    private List<Teacher> teacherList;

    public ClassRoom(String name) {
        this.name = name;
        teacherList = new ArrayList<>();
    }
}
