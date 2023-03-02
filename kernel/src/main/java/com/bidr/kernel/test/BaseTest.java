/**
 * Title: BaseTest.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-5 21:22
 * @description Project Name: Tanya
 * @Package: com.srct.service.frame
 */
package com.bidr.kernel.test;

import com.bidr.kernel.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@Slf4j
public abstract class BaseTest extends AbstractTestNGSpringContextTests {
    protected void log(Object... obj) {
        log.info(JsonUtil.toJson(obj));
    }
}

