package pyc.lopatuxin.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pyc.lopatuxin.budget.entity.Category;

import java.util.List;
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
}