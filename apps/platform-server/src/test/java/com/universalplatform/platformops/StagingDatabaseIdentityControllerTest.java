package com.universalplatform.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StagingDatabaseIdentityControllerTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);

    @Test
    void verifiesOnlyTheAllowlistedPostgresHostAndDatabase() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(
                "jdbc:postgresql://uep-staging-ref.supabase.co:5432/postgres?sslmode=require");
        var controller = new StagingDatabaseIdentityController(
                dataSource, authorization, "uep-staging-ref.supabase.co", "postgres");

        var result = controller.verifyDatabaseIdentity();

        assertThat(result).containsEntry("verified", true)
                .containsEntry("databaseHost", "uep-staging-ref.supabase.co")
                .containsEntry("databaseName", "postgres");
        verify(authorization).require(PermissionKey.OPERATIONS_MANAGE);
    }

    @Test
    void rejectsAProductionOrUnexpectedDatabaseBeforeSeederCanProceed() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(
                "jdbc:postgresql://production-ref.supabase.co:5432/postgres?sslmode=require");
        var controller = new StagingDatabaseIdentityController(
                dataSource, authorization, "uep-staging-ref.supabase.co", "postgres");

        assertThatThrownBy(controller::verifyDatabaseIdentity)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Connected database does not match");
    }

    @Test
    void rejectsMissingExpectedDatabaseIdentityAtConstruction() {
        assertThatThrownBy(() -> new StagingDatabaseIdentityController(
                dataSource, authorization, " ", "postgres"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected database host");
    }
}
