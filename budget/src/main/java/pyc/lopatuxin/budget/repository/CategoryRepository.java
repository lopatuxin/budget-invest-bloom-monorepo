package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pyc.lopatuxin.budget.entity.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с категориями расходов пользователя.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Возвращает все категории расходов для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список категорий пользователя
     */
    List<Category> findByUserId(UUID userId);

    /**
     * Находит категорию по идентификатору и идентификатору пользователя.
     *
     * @param id     идентификатор категории
     * @param userId идентификатор пользователя
     * @return Optional с категорией, если она принадлежит пользователю
     */
    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Находит категорию по названию и идентификатору пользователя.
     *
     * @param name   название категории
     * @param userId идентификатор пользователя
     * @return Optional с категорией, если она найдена
     */
    Optional<Category> findByNameAndUserId(String name, UUID userId);

    /**
     * Возвращает только пользовательские (не системные) категории для указанного пользователя.
     * Используется в пользовательских эндпоинтах — системные категории (например, «Инвестиции»)
     * в списках и выпадашках не отображаются.
     *
     * @param userId идентификатор пользователя
     * @return список пользовательских категорий
     */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.system = false")
    List<Category> findUserCategoriesByUserId(@Param("userId") UUID userId);

    /**
     * Ищет системную категорию пользователя по имени.
     *
     * @param userId идентификатор пользователя
     * @param name   название категории
     * @return Optional с системной категорией, если она найдена
     */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId AND c.name = :name AND c.system = true")
    Optional<Category> findSystemCategoryByUserIdAndName(@Param("userId") UUID userId,
                                                         @Param("name") String name);
}