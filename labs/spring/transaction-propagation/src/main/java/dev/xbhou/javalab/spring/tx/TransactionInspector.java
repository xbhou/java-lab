package dev.xbhou.javalab.spring.tx;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionInspector {

    private final DataSource dataSource;

    public TransactionInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void print(String scope) {
        Connection connection =
                DataSourceUtils.getConnection(dataSource);

        try {
            System.out.printf(
                    "%-28s transactionActive=%-5s connectionId=%d%n",
                    scope,
                    TransactionSynchronizationManager
                            .isActualTransactionActive(),
                    System.identityHashCode(connection)
            );
        } finally {
            DataSourceUtils.releaseConnection(
                    connection,
                    dataSource
            );
        }
    }
}
