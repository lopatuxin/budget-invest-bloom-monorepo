package pyc.lopatuxin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.repository.UserRoleRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String TEST_PASSWORD = "testPassword";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    public User createUser() {
        return User.builder()
                .email("test@example.com")
                .username("test_user")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .isActive(true)
                .isVerified(true)
                .build();
    }
}