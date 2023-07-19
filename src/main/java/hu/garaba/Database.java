package hu.garaba;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {
    private final Connection connection;

    public Database(Path path) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    public boolean isWhitelisted(long userId) {
        try (var st = connection.prepareStatement("SELECT 1 FROM USERS WHERE user_id = ?")) {
            st.setLong(1, userId);

            ResultSet resultSet = st.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public record TokenUsage(int promptToken, int completionToken) {}
    public TokenUsage getUsage(long userId) throws SQLException {
        try (var st = connection.prepareStatement("SELECT prompt_token, completion_token FROM USERS WHERE user_id = ?")) {
            st.setLong(1, userId);

            ResultSet resultSet = st.executeQuery();
            if (!resultSet.next()) {
                throw new IllegalArgumentException("No user found with userId " + userId);
            }
            return new TokenUsage(resultSet.getInt(1), resultSet.getInt(1));
        }
    }

    public void writeUsage(long userId, long deltaPromptToken, long deltaCompletionToken) throws SQLException {
        try (var st = connection.prepareStatement("UPDATE USERS SET prompt_token = prompt_token + ?, completion_token = completion_token + ? WHERE user_id = ?")) {
            st.setLong(1, deltaPromptToken);
            st.setLong(2, deltaCompletionToken);
            st.setLong(3, userId);

            int i = st.executeUpdate();
            if (i != 1) {
                throw new SQLException("Failed to update usage of user " + userId);
            }
        }
    }
}
