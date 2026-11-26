package com.guvi.recommendationsystem.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Transfers a fully bound statement to Spring, closing it when binding fails before that transfer. */
public final class JdbcStatements {
    private JdbcStatements() { }

    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    public static PreparedStatement generated(Connection connection, String sql, Binder binder) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        try {
            binder.bind(statement);
            return statement; // JdbcTemplate owns and closes the successfully returned statement.
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                statement.close();
            } catch (SQLException | RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
