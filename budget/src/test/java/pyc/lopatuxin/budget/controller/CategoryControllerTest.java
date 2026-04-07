package pyc.lopatuxin.budget.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import pyc.lopatuxin.budget.AbstractIntegrationTest;
import pyc.lopatuxin.budget.entity.Category;

import java.math.BigDecimal;
import java.util.stream.Stream;

import java.util.UUID;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Интеграционные тесты CategoryController")
class CategoryControllerTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/budget/categories";

    private UUID userId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        incomeRepository.deleteAll();
        capitalRecordRepository.deleteAll();
        categoryRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен создать категорию и вернуть статус 201 с корректным телом ответа")
    void shouldCreateCategoryAndReturn201WithCorrectBody() throws Exception {
        String requestBody = buildRequest(userId, "Продукты", "15000.00", "\uD83D\uDED2");

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.message", is("Категория успешно создана")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.body.id", notNullValue()))
                .andExpect(jsonPath("$.body.name", is("Продукты")))
                .andExpect(jsonPath("$.body.budget", comparesEqualTo(15000.00)))
                .andExpect(jsonPath("$.body.emoji", is("\uD83D\uDED2")));
    }

    @Test
    @DisplayName("Должен создать категорию без emoji")
    void shouldCreateCategoryWithoutEmoji() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "name": "Транспорт",
                    "budget": 5000.00
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body.name", is("Транспорт")))
                .andExpect(jsonPath("$.body.budget", comparesEqualTo(5000.00)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDataProvider")
    @DisplayName("Должен вернуть 400 при невалидных данных категории")
    void shouldReturn400WhenDataIsInvalid(String scenario, String requestBody) throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    static Stream<Arguments> invalidDataProvider() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        String userBlock = """
                "user": {"userId": "%s", "email": "test@example.com", "role": "USER", "sessionId": "%s"}""".formatted(uid, sid);

        return Stream.of(
                Arguments.of("name отсутствует (null)", "{%s, \"data\": {\"budget\": 10000.00}}".formatted(userBlock)),
                Arguments.of("name пустой (blank)", "{%s, \"data\": {\"name\": \"   \", \"budget\": 10000.00}}".formatted(userBlock)),
                Arguments.of("budget отсутствует (null)", "{%s, \"data\": {\"name\": \"Продукты\"}}".formatted(userBlock))
        );
    }

    @Test
    @DisplayName("Должен вернуть 400 при отрицательном budget")
    void shouldReturn400WhenBudgetIsNegative() throws Exception {
        String requestBody = buildRequest(userId, "Еда", "-100.00", null);

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии блока user")
    void shouldReturn400WhenUserBlockIsMissing() throws Exception {
        String requestBody = """
                {
                  "data": {
                    "name": "Продукты",
                    "budget": 10000.00
                  }
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Должен вернуть 400 при отсутствии блока data")
    void shouldReturn400WhenDataBlockIsMissing() throws Exception {
        String requestBody = """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  }
                }
                """.formatted(userId, UUID.randomUUID());

        mockMvc.perform(post(BASE_URL)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    private String buildRequest(UUID reqUserId, String name, String budget, String emoji) {
        String emojiField = emoji != null ? ", \"emoji\": \"%s\"".formatted(emoji) : "";
        return """
                {
                  "user": {
                    "userId": "%s",
                    "email": "test@example.com",
                    "role": "USER",
                    "sessionId": "%s"
                  },
                  "data": {
                    "name": "%s",
                    "budget": %s%s
                  }
                }
                """.formatted(reqUserId, UUID.randomUUID(), name, budget, emojiField);
    }

    @Nested
    @DisplayName("Обновление категории (POST /update)")
    class UpdateCategory {

        private static final String UPDATE_URL = BASE_URL + "/update";

        @Test
        @DisplayName("Должен обновить категорию и вернуть статус 200 с корректным телом ответа")
        void shouldUpdateCategoryAndReturn200WithCorrectBody() throws Exception {
            Category category = categoryRepository.save(Category.builder()
                    .userId(userId)
                    .name("Продукты")
                    .budget(new BigDecimal("15000.00"))
                    .emoji("\uD83D\uDED2")
                    .build());

            String requestBody = buildUpdateRequest(userId, category.getId(), "Еда", "20000.00");

            mockMvc.perform(post(UPDATE_URL)
                            .content(requestBody)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status", is(200)))
                    .andExpect(jsonPath("$.message", is("Категория успешно обновлена")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.body.id", is(category.getId().toString())))
                    .andExpect(jsonPath("$.body.name", is("Еда")))
                    .andExpect(jsonPath("$.body.budget", comparesEqualTo(20000.00)))
                    .andExpect(jsonPath("$.body.emoji", is("\uD83D\uDED2")));
        }

        @Test
        @DisplayName("Должен вернуть 404 когда категория не найдена")
        void shouldReturn404WhenCategoryNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();
            String requestBody = buildUpdateRequest(userId, nonExistentId, "Еда", "10000.00");

            mockMvc.perform(post(UPDATE_URL)
                            .content(requestBody)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", is("Категория не найдена")));
        }

        @Test
        @DisplayName("Должен вернуть 404 при попытке обновить категорию другого пользователя")
        void shouldReturn404WhenCategoryBelongsToAnotherUser() throws Exception {
            Category category = categoryRepository.save(Category.builder()
                    .userId(UUID.randomUUID())
                    .name("Чужая категория")
                    .budget(new BigDecimal("5000.00"))
                    .build());

            String requestBody = buildUpdateRequest(userId, category.getId(), "Моя категория", "10000.00");

            mockMvc.perform(post(UPDATE_URL)
                            .content(requestBody)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("Должен вернуть 409 при дубликате имени категории")
        void shouldReturn409WhenDuplicateCategoryName() throws Exception {
            categoryRepository.save(Category.builder()
                    .userId(userId)
                    .name("Еда")
                    .budget(new BigDecimal("10000.00"))
                    .build());

            Category categoryToUpdate = categoryRepository.save(Category.builder()
                    .userId(userId)
                    .name("Транспорт")
                    .budget(new BigDecimal("5000.00"))
                    .build());

            String requestBody = buildUpdateRequest(userId, categoryToUpdate.getId(), "Еда", "5000.00");

            mockMvc.perform(post(UPDATE_URL)
                            .content(requestBody)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", is(409)))
                    .andExpect(jsonPath("$.message", is("Категория с таким именем уже существует")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("pyc.lopatuxin.budget.controller.CategoryControllerTest#invalidUpdateDataProvider")
        @DisplayName("Должен вернуть 400 при невалидных данных обновления категории")
        void shouldReturn400WhenUpdateDataIsInvalid(String scenario, String requestBody) throws Exception {
            mockMvc.perform(post(UPDATE_URL)
                            .content(requestBody)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)));
        }

        private String buildUpdateRequest(UUID reqUserId, UUID categoryId, String name, String budget) {
            return """
                    {
                      "user": {
                        "userId": "%s",
                        "email": "test@example.com",
                        "role": "USER",
                        "sessionId": "%s"
                      },
                      "data": {
                        "categoryId": "%s",
                        "name": "%s",
                        "budget": %s
                      }
                    }
                    """.formatted(reqUserId, UUID.randomUUID(), categoryId, name, budget);
        }
    }

    static Stream<Arguments> invalidUpdateDataProvider() {
        UUID uid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        String userBlock = """
                "user": {"userId": "%s", "email": "test@example.com", "role": "USER", "sessionId": "%s"}""".formatted(uid, sid);

        return Stream.of(
                Arguments.of("categoryId отсутствует (null)",
                        "{%s, \"data\": {\"name\": \"Еда\", \"budget\": 10000.00}}".formatted(userBlock)),
                Arguments.of("name отсутствует (null)",
                        "{%s, \"data\": {\"categoryId\": \"%s\", \"budget\": 10000.00}}".formatted(userBlock, catId)),
                Arguments.of("name пустой (blank)",
                        "{%s, \"data\": {\"categoryId\": \"%s\", \"name\": \"   \", \"budget\": 10000.00}}".formatted(userBlock, catId)),
                Arguments.of("budget отсутствует (null)",
                        "{%s, \"data\": {\"categoryId\": \"%s\", \"name\": \"Еда\"}}".formatted(userBlock, catId)),
                Arguments.of("budget отрицательный",
                        "{%s, \"data\": {\"categoryId\": \"%s\", \"name\": \"Еда\", \"budget\": -100.00}}".formatted(userBlock, catId))
        );
    }
}
