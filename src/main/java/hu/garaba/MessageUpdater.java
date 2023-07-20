package hu.garaba;

import com.azure.ai.openai.models.ChatChoice;

import java.util.function.Consumer;

public class MessageUpdater {
    public static final System.Logger LOGGER = System.getLogger(MessageUpdater.class.getCanonicalName());

    private final Conversation.Message message;
    private final Consumer<Update> updateFn;
    private StringBuffer buffer = new StringBuffer();
    private int completionTokenCount = 0;

    public record Update(boolean isStart, boolean isFinished, int completionTokenCount, Conversation.Message message) {}

    public MessageUpdater(Consumer<Update> updateFn, Conversation.Message message) {
        this.updateFn = updateFn;
        this.message = message;
    }

    public void push(ChatChoice chatChoice) {
        MessageContent messageContent = message.content();

        boolean isStart = messageContent.isEmpty();
        completionTokenCount++;
        if (chatChoice.getDelta() != null && chatChoice.getDelta().getContent() != null) {
            String content = chatChoice.getDelta().getContent();
            buffer.append(content);

            if (buffer.toString().length() > 30) {
                messageContent.append(buffer.toString());
                updateFn.accept(new Update(isStart, false, completionTokenCount, message));

                buffer = new StringBuffer();
            }
        }

        if (chatChoice.getFinishReason() != null) {
            messageContent.append(buffer.toString());
            messageContent.finish();
            updateFn.accept(new Update(isStart, true, completionTokenCount, message));
        }
    }
}
