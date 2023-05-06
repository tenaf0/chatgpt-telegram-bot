package hu.garaba;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Bot extends TelegramLongPollingBot {

    public static final System.Logger LOGGER = System.getLogger(Bot.class.getName());

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String TELEGRAM_API_KEY = System.getenv("TELEGRAM_API_KEY");

    private final OpenAiService openAiService = new OpenAiService(OPENAI_API_KEY, Duration.ZERO);

    public Bot() {
        super(TELEGRAM_API_KEY);

        LOGGER.log(System.Logger.Level.INFO, "Started Bot");
    }

    @Override
    public String getBotUsername() {
        return "tenaf_test_bot";
    }

    private final Map<Long, List<Conversation>> userConversations = new ConcurrentHashMap<>();

    private int sendMessage(long userId, String message) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(userId)
                    .text(message)
                    .build();
            Message sentMessage = execute(sendMessage);

            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        var user = message.getFrom();

        LOGGER.log(System.Logger.Level.INFO, "{0}: {1}", user.getId(), message.getText());

        List<Conversation> conversations = userConversations.computeIfAbsent(user.getId(), k -> {
            LOGGER.log(System.Logger.Level.INFO, "Number of users: {0}", userConversations.size() + 1);
            var list = new ArrayList<Conversation>();
            list.add(new Conversation());
            return list;
        });

        if (message.isCommand() && message.getText().startsWith("/clear")) {
            conversations.add(new Conversation());
            sendMessage(user.getId(), "Conversation cleared");
            return;
        }

        Conversation conversation = conversations.get(conversations.size()-1);

        if (conversation.lastUpdate().isBefore(Instant.now().minus(10, ChronoUnit.MINUTES))) {
            sendMessage(user.getId(), "Conversation cleared due to timeout");
            conversation = new Conversation();
            conversations.add(conversation);
        }

        if (conversation.getTurnStream().findAny().isEmpty()) {
            conversation.recordMessage(ChatMessageRole.SYSTEM.value(),
                    "You are a chat assistant inside a Telegram Bot. Give concise answers.");
        }
        conversation.recordMessage(ChatMessageRole.USER.value(), message.getText());

        ChatCompletionRequest chatRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(conversation.getTurnStream().map(c -> new ChatMessage(c.role(), c.message())).toList())
                .n(1)
                .build();

        class MessageId {
            int id = -1;
            int diffSum = 0;
        }
        final var finalConversation = conversation;
        executor.submit(() -> {
            final MessageId messageId = new MessageId();
            finalConversation.initMessageReconstruction(ChatMessageRole.ASSISTANT.value(), (s, diff) -> {
                messageId.diffSum += diff.isLeft() ? diff.left() : 0;

                if (messageId.id == -1) {
                    if (s != null && !"".equals(s)) {
                        messageId.id = sendMessage(user.getId(), s);
                        messageId.diffSum = 0;
                    }
                } else {
                    if (messageId.diffSum > 10 || diff.isRight()) {
                        String postfix = (diff.isRight() && !"stop".equals(diff.right())) ? (" [" + diff.right().toUpperCase() + "]") : "";
                        editMessage(user.getId(), messageId.id, s + postfix);
                        messageId.diffSum = 0;
                    }
                }
            });
            openAiService.streamChatCompletion(chatRequest)
                    .doOnError(Throwable::printStackTrace)
                    .blockingForEach(finalConversation::addChunk);
        });
    }
}
