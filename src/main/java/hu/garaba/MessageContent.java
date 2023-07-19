package hu.garaba;

/**
 *
 */
public class MessageContent {
    private final StringBuffer buffer = new StringBuffer();
    private boolean isFinished = false;

    private MessageContent() {}

    public void append(String text) {
        if (isFinished) {
            throw new IllegalStateException("MessageContent has already been finished. Can't append anymore.");
        }

        if (text == null) {
            return;
        }
        buffer.append(text);
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public void finish() {
        this.isFinished = true;
        buffer.trimToSize();
    }

    public static MessageContent create(String text) {
        var messageContent = new MessageContent();
        messageContent.buffer.append(text);
        return messageContent;
    }

    public static MessageContent finished(String text) {
        var messageContent = new MessageContent();
        messageContent.isFinished = true;
        messageContent.buffer.append(text);
        return messageContent;
    }

    @Override
    public String toString() {
        return buffer + (isFinished ? "" : "...");
    }
}
