package com.easypan;

import com.easypan.component.RedisComponent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.Disabled("Disabled due to H2/MyBatis-Flex compatibility issues in test environment")
class EasyPanApplicationTests {

    @MockBean(name = "initRun")
    private InitRun initRun;

    @MockBean
    private RedisComponent redisComponent;

    @org.junit.jupiter.api.Disabled("Disabled due to H2/MyBatis-Flex compatibility issues in test environment")
    @Test
    void contextLoads() {
    }

}
