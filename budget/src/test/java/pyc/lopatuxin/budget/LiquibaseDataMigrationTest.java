package pyc.lopatuxin.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that changesets carrying the owner's personal data (005-import-legacy-budget,
 * 008-adjust-initial-balance — both tagged {@code context: data-migration}) do not run against
 * a fresh environment, the test database included. Only changesets without a context tag run
 * by default; these two intentionally stay out of it.
 */
@DisplayName("Liquibase — миграции с персональными данными не попадают в тестовую БД")
class LiquibaseDataMigrationTest extends AbstractIntegrationTest {

    private static final UUID OWNER_USER_ID = UUID.fromString("d9e08bd2-f35a-4c85-a85d-52dcaf446bbd");

    @Test
    @DisplayName("005-import-legacy-budget: категории владельца не должны быть созданы в тестовой БД")
    void ownerCategories_shouldNotExistInTestDatabase() {
        assertThat(categoryRepository.findUserCategoriesByUserId(OWNER_USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("008-adjust-initial-balance: корректировка стартового баланса владельца не должна быть создана в тестовой БД")
    void ownerInitialBalanceIncome_shouldNotExistInTestDatabase() {
        boolean hasOwnerIncome = incomeRepository.findAll().stream()
                .anyMatch(income -> OWNER_USER_ID.equals(income.getUserId()));

        assertThat(hasOwnerIncome).isFalse();
    }
}
