package pyc.lopatuxin.budget;

import org.springframework.boot.SpringApplication;

public class TestBudgetApplication {

    public static void main(String[] args) {
        SpringApplication.from(BudgetApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
