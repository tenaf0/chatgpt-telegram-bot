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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Bot extends TelegramLongPollingBot {
    private final OpenAiService openAiService = new OpenAiService(System.getenv("OPENAI_API_KEY"));

    public Bot() {
        super(System.getenv("TELEGRAM_API_KEY"));
    }

    @Override
    public String getBotUsername() {
        return "tenaf_test_bot";
    }

    private final Map<Long, List<Conversation>> userConversations = new ConcurrentHashMap<>();

    private int sendMessage(long userId, String message) {
        try {
            SendMessage sendMessage = SendMessage.builder().chatId(userId).text(message).build();
            Message sentMessage = execute(sendMessage);

            return sentMessage.getMessageId();
        } catch (TelegramApiException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void editMessage(long userId, long messageId, String newText) {
        try {
            EditMessageText editRequest = EditMessageText.builder().chatId(userId).messageId((int) messageId).text(newText).build();
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

        List<Conversation> conversations = userConversations.computeIfAbsent(user.getId(), k -> {
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

        if (conversation.startTime().isBefore(Instant.now().minus(10, ChronoUnit.MINUTES))) {
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
                .maxTokens(100)
                .build();

        System.out.println(chatRequest);

        class MessageId {
            int id = -1;
            int diffSum = 0;
        }
        final var finalConversation = conversation;
        executor.submit(() -> {
            final MessageId messageId = new MessageId();
            finalConversation.initMessageReconstruction(ChatMessageRole.ASSISTANT.value(), (s, diff) -> {
                messageId.diffSum += Math.max(0, diff);

                if (messageId.id == -1) {
                    if (s != null && !"".equals(s)) {
                        messageId.id = sendMessage(user.getId(), s);
                        messageId.diffSum = 0;
                    }
                } else {
                    if (messageId.diffSum > 10 || diff == -1) {
                        editMessage(user.getId(), messageId.id, s);
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
