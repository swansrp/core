package com.bidr.authorization.constants.group;

import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.kernel.utils.FuncUtil;
import lombok.Data;
import org.reflections.Reflections;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Title: AcGroupManager
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 09:53
 */
@Data
@Component
public class AcGroupManager implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        Reflections reflections = new Reflections("com.bidr");
        List<AcGroupType> acGroupTypeList = new ArrayList<>();
        Set<Class<? extends Group>> groupList = reflections.getSubTypesOf(Group.class);
        if (FuncUtil.isNotEmpty(groupList)) {
            for (Class<? extends Group> aClass : groupList) {

            }
        }
    }
}
