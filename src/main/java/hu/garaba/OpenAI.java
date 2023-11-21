package hu.garaba;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.util.IterableStream;

import java.util.function.Consumer;

public class OpenAI {
    public static final System.Logger LOGGER = System.getLogger(OpenAI.class.getCanonicalName());
    private enum Model {
        GPT_3_5_TURBO("gpt-3.5-turbo"),
        GPT_4("gpt-4-1106-preview");

        public final String name;
        Model(String name) {
            this.name = name;
        }
    }

    private final OpenAIClient client;

    public OpenAI(String OPENAI_API_KEY) {
        this.client = new OpenAIClientBuilder()
                .credential(new NonAzureOpenAIKeyCredential(OPENAI_API_KEY))
                .buildClient();
    }

    public void send(String userId, Conversation conv, Consumer<MessageUpdater.Update> updateFn) {
        LOGGER.log(System.Logger.Level.DEBUG, "Sending message to OpenAI");
        IterableStream<ChatCompletions> completions = client.getChatCompletionsStream(Model.GPT_4.name,
                new ChatCompletionsOptions(conv.packageMessages())
                        .setUser(userId));

        MessageUpdater chatChoiceConsumer = conv.streamMessage(ChatRole.ASSISTANT, updateFn);

        completions.stream().forEach(c -> {
            var choice = c.getChoices().get(0);
            chatChoiceConsumer.push(choice);
        });
    }

    public static int approximateTokens(String text) {
        return text.length() / 4;
    }
}
