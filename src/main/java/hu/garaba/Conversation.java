package hu.garaba;

import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import hu.garaba.util.MutInteger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Conversation {
    public record Message(Instant date, MutInteger messageId, ChatRole role, MessageContent content) {}
    private final List<Message> messages = new ArrayList<>();
    private final AtomicInteger promptToken = new AtomicInteger(0);
    private final AtomicInteger completionToken = new AtomicInteger(0);

    public void recordMessage(ChatRole role, MessageContent content) {
        messages.add(new Message(Instant.now(), new MutInteger(), role, content));
    }

    public MessageUpdater streamMessage(ChatRole role, Consumer<MessageUpdater.Update> updateFn) {
        int promptTokens = messages.stream().mapToInt(m -> OpenAI.approximateTokens(m.content.toString())).sum();
        promptToken.addAndGet(promptTokens);

        Message message = new Message(Instant.now(), new MutInteger(), role, MessageContent.create(""));
        messages.add(message);

        return new MessageUpdater(u -> {
            completionToken.getAndAdd(u.completionTokenCount());
            updateFn.accept(u);
        }, message);
    }

    public record TokenUsage(int promptToken, int completionToken) {}

    /**
     * Zeroes out the prompt and completion token counts for this Conversation
     */
    public TokenUsage resetTokenUsage() {
        return new TokenUsage(promptToken.getAndSet(0), completionToken.getAndSet(0));
    }

    public Instant latestUpdate() {
        return messages.get(messages.size() - 1).date;
    }

    public List<ChatMessage> packageMessages() {
        return messages.stream().map(m -> new ChatMessage(m.role).setContent(m.content.toString())).toList();
    }
}