package hu.garaba;

import com.azure.ai.openai.models.ChatRole;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Summarizer {
    public static boolean isValidURL(String url) {
        try {
            new URI(url);

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String extractArticle(URI uri) throws IOException {
        Process extractorProcess = new ProcessBuilder("trafilatura", "-u", uri.toString())
                .start();

        try (BufferedReader bufferedReader = extractorProcess.inputReader(StandardCharsets.UTF_8)) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        }
    }

    public static Conversation summarizeArticle(OpenAI openAI, String text, Consumer<MessageUpdater.Update> updateFn) {
        Conversation conversation = new Conversation();
        conversation.recordMessage(ChatRole.SYSTEM,
                MessageContent.finished("You will receive an article's content. You are to summarize it."));
        conversation.recordMessage(ChatRole.SYSTEM, MessageContent.finished(text));

        openAI.send("summarizer", conversation, updateFn);
        return conversation;
    };
}
