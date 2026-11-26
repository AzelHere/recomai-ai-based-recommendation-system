package com.guvi.recommendationsystem.dao;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcStatementsTest {
    @Test void successTransfersOwnershipWithoutClosingTheConnectionOrStatement() throws Exception {
        var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
        when(connection.prepareStatement("insert",Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        assertThat(JdbcStatements.generated(connection,"insert",s->s.setString(1,"value"))).isSameAs(statement);
        verify(statement).setString(1,"value");verify(statement,never()).close();verify(connection,never()).close();
    }
    @Test void bindingFailureClosesTheStatementAndPreservesTheOriginalException() throws Exception {
        var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
        when(connection.prepareStatement("insert",Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        var bindingFailure=new SQLException("binding failed");var cleanupFailure=new SQLException("cleanup failed");
        doThrow(bindingFailure).when(statement).setLong(1,42L);doThrow(cleanupFailure).when(statement).close();
        assertThatThrownBy(()->JdbcStatements.generated(connection,"insert",s->s.setLong(1,42L)))
            .isSameAs(bindingFailure).satisfies(e->assertThat(e.getSuppressed()).containsExactly(cleanupFailure));
        verify(statement).close();verify(connection,never()).close();
    }
    @Test void uncheckedBindingFailuresAreAlsoClosed() throws Exception {
        var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
        when(connection.prepareStatement("insert",Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        var failure=new IllegalArgumentException("invalid binding");
        assertThatThrownBy(()->JdbcStatements.generated(connection,"insert",s->{throw failure;})).isSameAs(failure);
        verify(statement).close();verify(connection,never()).close();
    }
    @Test void preparationFailureDoesNotCloseTheTransactionConnection() throws Exception {
        var connection=mock(Connection.class);var failure=new SQLException("prepare failed");
        when(connection.prepareStatement("insert",Statement.RETURN_GENERATED_KEYS)).thenThrow(failure);
        assertThatThrownBy(()->JdbcStatements.generated(connection,"insert",s->fail("binder must not run"))).isSameAs(failure);
        verify(connection,never()).close();
    }
}
