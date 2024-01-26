package com.bidr.authorization.constants.group;

import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.authorization.dao.repository.AcGroupTypeService;
import com.bidr.kernel.utils.FuncUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
@RequiredArgsConstructor
public class AcGroupManager implements CommandLineRunner {

    @Value("${my.base-package}")
    private String basePackage;

    private final AcGroupTypeService acGroupTypeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<? extends Group>> groupList = reflections.getSubTypesOf(Group.class);
        if (FuncUtil.isNotEmpty(groupList)) {
            for (Class<? extends Group> clazz : groupList) {
                if (Enum.class.isAssignableFrom(clazz) && Group.class.isAssignableFrom(clazz)) {
                    for (Group enumItem : clazz.getEnumConstants()) {
                        AcGroupType item = buildAcGroupType(enumItem);
                        acGroupTypeService.delete(item);
                        acGroupTypeService.insert(item);
                    }
                }
            }
        }
    }

    private AcGroupType buildAcGroupType(Group enumItem) {
        AcGroupType acGroupType = new AcGroupType();
        acGroupType.setId(enumItem.name());
        acGroupType.setName(enumItem.getRemark());
        return acGroupType;
    }
}
