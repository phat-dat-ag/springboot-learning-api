package com.japaneselearning.learning.metrics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Actual Hikari tracking with a JDBC stub; no external database or pool sizing changes.
 */
class HikariPoolMetricsTest {
    @Test
    void poolAndAcquisitionMetersHaveOnlyBoundedPoolIdentity() throws Exception {
        var registry = new SimpleMeterRegistry();
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(true);
        var config = new HikariConfig();
        config.setDataSource(dataSource);
        config.setPoolName("metrics-fixture");
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(2);
        config.setInitializationFailTimeout(-1);
        config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
        try (var pool = new HikariDataSource(config)) {
            try (var borrowed = pool.getConnection()) {
                assertThat(registry.get("hikaricp.connections.active").gauge().value()).isEqualTo(1);
                assertThat(registry.get("hikaricp.connections.max").gauge().value()).isEqualTo(2);
                assertThat(registry.get("hikaricp.connections.pending").gauge().value()).isZero();
                assertThat(registry.get("hikaricp.connections.acquire").timer().count()).isEqualTo(1);
            }
            // Hikari caches pool snapshots for one second; wait for the next refresh.
            for (int i = 0; i < 150 && registry.get("hikaricp.connections.idle").gauge().value() != 1; i++)
                Thread.sleep(10);
            assertThat(registry.get("hikaricp.connections.idle").gauge().value()).isEqualTo(1);
            assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(1));
        } finally {
            registry.close();
        }
    }
}
