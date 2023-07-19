package hu.garaba;

import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import hu.garaba.util.MutInteger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Conversation {
    public record Message(Instant date, MutInteger messageId, ChatRole role, MessageContent content) {}
    private final List<Message> messages = new ArrayList<>();
    private int promptToken = 0;
    private int completionToken = 0;

    public void recordMessage(ChatRole role, MessageContent content) {
        messages.add(new Message(Instant.now(), new MutInteger(), role, content));
        promptToken += OpenAI.approximateTokens(content.toString());
    }

    public MessageUpdater streamMessage(ChatRole role, Consumer<MessageUpdater.Update> updateFn) {
        Message message = new Message(Instant.now(), new MutInteger(), role, MessageContent.create(""));
        messages.add(message);

        return new MessageUpdater(u -> {
            completionToken += u.completionTokenCount();
            updateFn.accept(u);
        }, message);
    }

    public int getPromptToken() {
        return promptToken;
    }

    public int getCompletionToken() {
        return completionToken;
    }

    public Instant latestUpdate() {
        return messages.get(messages.size() - 1).date;
    }

    public List<ChatMessage> packageMessages() {
        return messages.stream().map(m -> new ChatMessage(m.role).setContent(m.content.toString())).toList();
    }
}