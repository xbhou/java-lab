package dev.xbhou.javalab.spring.tx;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class TransactionConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:txlab;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(
            DataSource dataSource
    ) {
        DataSourceTransactionManager manager =
                new DataSourceTransactionManager(dataSource);

        manager.setNestedTransactionAllowed(true);

        return manager;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public LedgerRepository ledgerRepository(
            JdbcTemplate jdbcTemplate
    ) {
        return new LedgerRepository(jdbcTemplate);
    }

    @Bean
    public TransactionInspector transactionInspector(
            DataSource dataSource
    ) {
        return new TransactionInspector(dataSource);
    }

    @Bean
    public InnerService innerService(
            LedgerRepository repository,
            TransactionInspector inspector
    ) {
        return new InnerService(repository, inspector);
    }

    @Bean
    public OuterService outerService(
            LedgerRepository repository,
            InnerService innerService,
            TransactionInspector inspector
    ) {
        return new OuterService(
                repository,
                innerService,
                inspector
        );
    }

    @Bean
    public SelfInvocationService selfInvocationService(
            LedgerRepository repository,
            TransactionInspector inspector
    ) {
        return new SelfInvocationService(
                repository,
                inspector
        );
    }
}
