package hu.garaba;

import com.azure.ai.openai.models.ChatRole;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class Bot extends TelegramLongPollingBot {
    private static final System.Logger LOGGER = System.getLogger(Bot.class.getCanonicalName());

    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private static final String TELEGRAM_API_KEY = Objects.requireNonNull(System.getenv("TELEGRAM_API_KEY"));

    private final OpenAI openAI = new OpenAI(OPENAI_API_KEY);
    private final Database db;

    private static final Duration CONVERSATION_CLEAR_PERIOD = Duration.of(15, ChronoUnit.MINUTES);

    public Bot() throws SQLException {
        super(TELEGRAM_API_KEY);
        this.db = new Database(Path.of("bot.db"));

        LOGGER.log(System.Logger.Level.INFO, "Starting bot");

        ScheduledExecutorService conversationClearerExecutor = Executors.newScheduledThreadPool(1);
        conversationClearerExecutor.scheduleWithFixedDelay(() -> {
            LOGGER.log(System.Logger.Level.DEBUG, "Started flushing conversation statistics.");
            int i = 0;
            Instant now = Instant.now();
            for (var entry : conversationMap.entrySet()) {
                if (entry.getValue().latestUpdate().isBefore(now.minus(CONVERSATION_CLEAR_PERIOD))) {
                    boolean hasFlushed = flushConversationStatistics(entry.getKey(), entry.getValue());
                    if (hasFlushed)
                        i++;
                }
            }
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Finished flushing conversation statistics to db. Have flushed " + i + " entries.");
        }, (long) (CONVERSATION_CLEAR_PERIOD.toMinutes() * 1.5), CONVERSATION_CLEAR_PERIOD.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public String getBotUsername() {
        return "tenaf_test_bot2";
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<Long, Conversation> conversationMap = new ConcurrentHashMap<>();

    @Override
    public synchronized void onClosing() {
        LOGGER.log(System.Logger.Level.DEBUG, "Bot shutting down");
        super.onClosing();
        db.close();
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        User user = message != null ? message.getFrom() : null;

        executor.submit(() -> {
            if (user == null || !db.isWhitelisted(user.getId())) {
                if (user != null) {
                    sendMessage(user.getId(), "You are not authorized to access this bot.");
                }
                throw new SecurityException("User " + user + " accessed the bot, but was not found in the whitelist table.");
            }

            if (message.isCommand()) {
                handleCommand(user, message.getText());
            } else {
                if (!message.getText().contains(" ") && Summarizer.isValidURL(message.getText())) {
                    handleCommand(user, "/summarize " + message.getText());
                } else {
                    handleConversation(user, message.getText());
                }
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
            Conversation.TokenUsage usage = null;
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
        } else if (text.startsWith("/summarize")) {
            String[] words = text.split("\\s+");
            if (words.length < 2 || !Summarizer.isValidURL(words[1])) {
                sendMessage(user.getId(),
                        "The command is ill-formed, or you provided an illegal URL. The correct syntax: /summarize <url>");
            } else {
                URI uri = URI.create(words[1]);
                try {
                    String textToSummarize;
                    if (Objects.equals(uri.getHost(), "www.youtube.com")) {
                        sendMessage(user.getId(), "Summarizing video transcript at " + uri + ":");
                        textToSummarize = Summarizer.extractVideoTranscript(uri);
                    } else {
                        sendMessage(user.getId(), "Summarizing article at " + uri + ":");
                        textToSummarize = Summarizer.extractArticle(uri);
                    }

                    Conversation conv = Summarizer.summarizeArticle(openAI, textToSummarize, u -> {
                        if (u.isStart()) {
                            u.message().messageId().value = sendMessage(user.getId(), u.message().content().toString());
                        } else {
                            if (u.message().messageId().value == null) {
                                throw new IllegalStateException("A message should be sent before it can be edited");
                            }
                            editMessage(user.getId(), u.message().messageId().value, u.message().content().toString());
                        }
                    });
                    flushConversationStatistics(user.getId(), conv);
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to extract article at " + uri, e);
                    sendMessage(user.getId(), "Failed to extract the linked article");
                }
            }
        }
    }

    private void handleConversation(User user, String text) {
        Conversation conv = conversationMap.computeIfAbsent(user.getId(), k -> initConversation(user));

        if (conv.latestUpdate().isBefore(Instant.now().minus(CONVERSATION_CLEAR_PERIOD))) {
            clearConversation(user, conv);
            conv = initConversation(user);
            conversationMap.put(user.getId(), conv);
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
        flushConversationStatistics(user.getId(), conv);
        sendMessage(user.getId(), "Conversation cleared");
    }

    /**
     * @param userId
     * @param conv
     * @return true, if the Conversation's tokenUsage is not 0.
     */
    private boolean flushConversationStatistics(long userId, Conversation conv) {
        Conversation.TokenUsage tokenUsage = conv.resetTokenUsage();
        if (!(tokenUsage.promptToken() == 0 && tokenUsage.completionToken() == 0)) {
            try {
                db.writeUsage(userId, tokenUsage);
                return true;
            } catch (SQLException e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to increase tokeCount of user with id " + userId, e);
            }
        }

        return false;
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
