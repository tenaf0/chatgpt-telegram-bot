package hu.garaba;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {
    private static final System.Logger LOGGER = System.getLogger(Database.class.getCanonicalName());

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

    public Conversation.TokenUsage getUsage(long userId) throws SQLException {
        try (var st = connection.prepareStatement("SELECT prompt_token, completion_token FROM USERS WHERE user_id = ?")) {
            st.setLong(1, userId);

            ResultSet resultSet = st.executeQuery();
            if (!resultSet.next()) {
                throw new IllegalArgumentException("No user found with userId " + userId);
            }
            return new Conversation.TokenUsage(resultSet.getInt(1), resultSet.getInt(2));
        }
    }

    public void writeUsage(long userId, Conversation.TokenUsage tokenUsage) throws SQLException {
        try (var st = connection.prepareStatement("UPDATE USERS SET prompt_token = prompt_token + ?, completion_token = completion_token + ? WHERE user_id = ?")) {
            st.setLong(1, tokenUsage.promptToken());
            st.setLong(2, tokenUsage.completionToken());
            st.setLong(3, userId);

            int i = st.executeUpdate();
            if (i != 1) {
                throw new SQLException("Failed to update usage of user " + userId);
            }
        }
    }

    public void close() {
        try {
            LOGGER.log(System.Logger.Level.DEBUG, "Closing database connection");
            connection.close();
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.WARNING, e);
        }
    }
}
