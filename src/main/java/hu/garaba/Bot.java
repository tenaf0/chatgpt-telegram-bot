package hu.garaba;

import com.azure.ai.openai.models.ChatRole;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Bot extends TelegramLongPollingBot {
    public static final System.Logger LOGGER = System.getLogger(Bot.class.getCanonicalName());

    private static final String TELEGRAM_BOT_KEY = System.getenv("TELEGRAM_API_KEY");

    private final OpenAI openAI = new OpenAI(System.getenv("OPENAI_API_KEY"));
    private final Database db;

    public Bot() throws SQLException {
        super(TELEGRAM_BOT_KEY);
        this.db = new Database(Path.of("bot.db"));

        LOGGER.log(System.Logger.Level.INFO, "Starting bot");
    }

    @Override
    public String getBotUsername() {
        return "tenaf_test_bot2";
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<Long, Conversation> conversationMap = new ConcurrentHashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        User user = message.getFrom();

        executor.submit(() -> {
            if (!db.isWhitelisted(user.getId())) {
                sendMessage(user.getId(), "You are not authorized to access this bot.");
                throw new SecurityException("User " + user + " accessed the bot, but was not found in the whitelist table.");
            }

            if (message.isCommand()) {
                handleCommand(user, message.getText());
            } else {
                handleConversation(user, message.getText());
            }
        });
    }

    private void handleCommand(User user, String text) {
        if (text.startsWith("/clear")) {
            Conversation conv = conversationMap.get(user.getId());
            if (conv != null) {
                clearConversation(user, conv);
            }
        } else if (text.startsWith("/usage")) {
            Database.TokenUsage usage = null;
            try {
                usage = db.getUsage(user.getId());
            } catch (SQLException e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to query tokeCount of user " + user, e);
            }

            if (usage != null) {
                sendMessage(user.getId(), "Your token usage: " + usage);
            } else {
                sendMessage(user.getId(), "Failed to query your usage count.");
            }
        }
    }

    private void handleConversation(User user, String text) {
        Conversation conv = conversationMap.computeIfAbsent(user.getId(), k -> initConversation(user));

        if (conv.latestUpdate().isBefore(Instant.now().minus(15, ChronoUnit.MINUTES))) {
            clearConversation(user, conv);
            conv = initConversation(user);
        }

        conv.recordMessage(ChatRole.USER, MessageContent.finished(text));

        openAI.send(Long.toString(user.getId()) /* TODO: We might not want to send the telegram id to OpenAI */,
                conv, u -> {
            if (u.isStart()) {
                u.message().messageId().value = sendMessage(user.getId(), u.message().content().toString());
            } else {
                if (u.message().messageId().value == null) {
                    throw new IllegalStateException("A message should be sent before it can be edited");
                }
                editMessage(user.getId(), u.message().messageId().value, u.message().content().toString());
            }
        });
    }

    private Conversation initConversation(User user) {
        Conversation c = new Conversation();
        c.recordMessage(ChatRole.SYSTEM,
                MessageContent.finished("You are a chat assistant inside a Telegram Bot talking with "
                        + user.getFirstName() + ". Give concise answers."));
        return c;
    }

    private void clearConversation(User user, Conversation conv) {
        try {
            db.writeUsage(user.getId(), conv.getPromptToken(), conv.getCompletionToken());
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failed to increase tokeCount of user " + user, e);
        }
        sendMessage(user.getId(), "Conversation cleared");
    }

    private int sendMessage(long userId, String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(userId)
                    .text(message)
                    .build();
            Message sentMessage = execute(sendMessage);

            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failure at sending message", e);
            throw new RuntimeException(e);
        }
    }

    private void editMessage(long userId, long messageId, String newText) {
        try {
            EditMessageText editRequest = EditMessageText.builder()
                    .chatId(userId)
                    .messageId((int) messageId)
                    .text(newText)
                    .build();
            execute(editRequest);
        } catch (TelegramApiException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Failure at editing message", e);
        }
    }
}
