package hu.garaba;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Conversation {
    private final Instant startTime;

    public Conversation() {
        this.startTime = Instant.now();
    }

    public Instant startTime() {
        return startTime;
    }

    public record Turn(String role, String message) {}
    private final List<Turn> turns = new ArrayList<>();

    public void recordMessage(String role, String message) {
        turns.add(new Turn(role, message));
    }


    private boolean isMessageReconstructionInProcess = false;
    private String role = null;
    private StringBuilder sb = null;
    private BiConsumer<String, Integer> updateFn = null;

    public void initMessageReconstruction(String role, BiConsumer<String, Integer> updateFn) {
        if (isMessageReconstructionInProcess) {
            throw new IllegalStateException("There is a message reconstruction already in process");
        }
        isMessageReconstructionInProcess = true;
        this.role = role;
        this.sb = new StringBuilder();
        this.updateFn = updateFn;
    }

    public void addChunk(ChatCompletionChunk chunk) {
        ChatCompletionChoice choice = chunk.getChoices().get(0);
        String msg = choice.getMessage().getContent();

        int length = 0;
        if (msg != null) {
            sb.append(msg);
            length = msg.length();
        }
        updateFn.accept(sb.toString(), choice.getFinishReason() != null ? -1 : length);
        if (choice.getFinishReason() != null) {
            closeMessageReconstruction();
        }
    }

    public void closeMessageReconstruction() {
        isMessageReconstructionInProcess = false;
        recordMessage(role, sb.toString());
        this.role = null;
        this.sb = null;
        this.updateFn = null;
    }

    public Stream<Turn> getTurnStream() {
        return turns.stream();
    }
}
