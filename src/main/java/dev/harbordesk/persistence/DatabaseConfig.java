package dev.harbordesk.persistence;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Two roles per database: the owner for migrations and onboarding, harbor_app for every request. */
@Configuration
public class DatabaseConfig {

    static HikariDataSource hikari(String url, String user, String password, int size) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setMaximumPoolSize(size);
        ds.setMinimumIdle(0);
        return ds;
    }

    static void migrate(DataSource owner) {
        Flyway.configure().dataSource(owner).locations("classpath:db/migration").load().migrate();
    }

    @Bean(destroyMethod = "close")
    HikariDataSource adminDataSource(DbProperties p) {
        HikariDataSource ds = hikari(p.url(), p.adminUser(), p.adminPassword(), 3);
        migrate(ds);
        return ds;
    }

    @Bean(destroyMethod = "close")
    HikariDataSource poolDataSource(DbProperties p, @Qualifier("adminDataSource") DataSource migrated) {
        return hikari(p.url(), p.appUser(), p.appPassword(), p.poolSize());
    }

    @Bean
    @Primary
    DataSource dataSource(DbProperties p, @Qualifier("poolDataSource") DataSource pool) {
        return new TenantRoutingDataSource(pool,
                db -> hikari(p.urlFor(db), p.appUser(), p.appPassword(), p.poolSize()));
    }

    @Bean
    DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate transactionTemplate(DataSourceTransactionManager tm) {
        return new TransactionTemplate(tm);
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
