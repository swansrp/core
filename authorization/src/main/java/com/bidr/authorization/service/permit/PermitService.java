package com.bidr.authorization.service.permit;

import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: PermitService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 14:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermitService {

    private final AcMenuService acMenuService;
    private final AcUserService acUserService;

    public List<PermitInfo> getPermitListByCustomerNumber(String customerNumber, String clientType) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        return null;
    }
}
