package org.enterpriseknowledgeassitance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=unused"})
@Import(PostgresTestSupport.class)
class EnterpriseKnowledgeAssitanceApplicationTests {

    @Test
    void contextLoads() {
    }

}
