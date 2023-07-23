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
    private static final System.Logger LOGGER = System.getLogger(Summarizer.class.getCanonicalName());

    public static boolean isValidURL(String url) {
        try {
            new URI(url);

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String extractArticle(URI uri) throws IOException {
        LOGGER.log(System.Logger.Level.DEBUG, "Starting extraction of article at " + uri.getHost());
        Process extractorProcess = new ProcessBuilder("trafilatura", "-u", uri.toString())
                .start();

        try (BufferedReader bufferedReader = extractorProcess.inputReader(StandardCharsets.UTF_8)) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        }
    }

    public static Conversation summarizeArticle(OpenAI openAI, String text, Consumer<MessageUpdater.Update> updateFn) {
        Conversation conversation = new Conversation();
        conversation.recordMessage(ChatRole.SYSTEM,
                MessageContent.finished("You are to provide a comprehensive summary of the given text. The summary should cover all the key points and main ideas presented in the original text, while also condensing the information into a concise and easy-to-understand format. Please ensure that the summary includes relevant details and examples that support the main ideas, while avoiding any unnecessary information or repetition. The length of the summary should be appropriate for the length and complexity of the original text, providing a clear and accurate overview without omitting any important information."));
        conversation.recordMessage(ChatRole.SYSTEM, MessageContent.finished(text));

        openAI.send("summarizer", conversation, updateFn);
        return conversation;
    }
}
