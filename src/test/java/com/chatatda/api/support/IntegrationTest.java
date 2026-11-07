package com.chatatda.api.support;

import com.chatatda.api.common.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSupportConfig.class)
public abstract class IntegrationTest {

    public static final String TEST_JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.seed.location", () -> "classpath:test-fixtures/");
        registry.add("pricing.conversion-rate", () -> "0.06");
        registry.add("pricing.baseline-mgmt-fee", () -> "71000");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected FakeMailPort mail;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        clock.reset();
        mail.clear();
        jdbc.execute("truncate table users, email_codes restart identity cascade");
    }

    protected String bearer(long userId) {
        return "Bearer " + jwtProvider.issue(userId);
    }
}
