package hu.garaba;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.IterableStream;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    private final HttpClient client;
    private final HttpRequest.Builder requestBuilder;

    public OpenAI(String OPENAI_API_KEY) {
        this.client = HttpClient.newHttpClient();
        this.requestBuilder =  HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OPENAI_API_KEY);

//        this.client = new OpenAIClientBuilder()
//                .credential(new KeyCredential(OPENAI_API_KEY))
//                .buildClient();
    }

    record JSONMessage(String role, String content) {}

    public void send(String userId, Conversation conv, Consumer<MessageUpdater.Update> updateFn) {
        LOGGER.log(System.Logger.Level.DEBUG, "Sending message to OpenAI");

        String body = """
                {
                  "model": "gpt-4-1106-preview",
                  "stream": true,
                  "messages":
                """ + JSON.toJSONString(conv.packageMessages().stream().map(m -> new JSONMessage(m.getRole().toString(), m.getContent())).toList()) +
                """  
                        }
                        """;
        LOGGER.log(System.Logger.Level.DEBUG, body);
        HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();

        try {
            HttpResponse<Stream<String>> response = client.send(request,HttpResponse.BodyHandlers.ofLines());


//            IterableStream<ChatCompletions> completions = client.getChatCompletionsStream(Model.GPT_4.name,
//                    new ChatCompletionsOptions(conv.packageMessages())
//                            .setUser(userId));

            MessageUpdater chatChoiceConsumer = conv.streamMessage(ChatRole.ASSISTANT, updateFn);

            response.body().forEach(l -> {
                LOGGER.log(System.Logger.Level.DEBUG, l);

                if (l != null && l.startsWith("data:")) {
                    if (l.equals("data: [DONE]")) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Finished");
                        var chatChoice = new MessageUpdater.ChatChoice(CompletionsFinishReason.STOPPED, new ChatMessage(ChatRole.ASSISTANT, null));
                        chatChoiceConsumer.push(chatChoice);
                    }
                    try {
                        JSONObject data = JSON.parseObject(l.substring("data:".length()));
                        JSONObject choice = data.getJSONArray("choices").getJSONObject(0);

                        if (Objects.equals(choice.getString("finish_reason"), "stop")) {
                            LOGGER.log(System.Logger.Level.DEBUG, "Finished");
                            var chatChoice = new MessageUpdater.ChatChoice(CompletionsFinishReason.STOPPED, new ChatMessage(ChatRole.ASSISTANT, null));
                            chatChoiceConsumer.push(chatChoice);
                        } else {
                            var chatChoice = new MessageUpdater.ChatChoice(null, new ChatMessage(ChatRole.ASSISTANT, choice.getJSONObject("delta").getString("content")));
                            chatChoiceConsumer.push(chatChoice);
                        }

                        LOGGER.log(System.Logger.Level.DEBUG, choice);
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Could not parse");
                    }

                }
            });

//            var chatChoice = new MessageUpdater.ChatChoice(CompletionsFinishReason.STOPPED, new ChatMessage(ChatRole.ASSISTANT, response.body()));
//            chatChoiceConsumer.push(chatChoice);

//            completions.stream().forEach(c -> {
//                var choice = c.getChoices().get(0);
//                chatChoiceConsumer.push(choice);
//            });
        } catch (IOException | InterruptedException exception) {
            LOGGER.log(System.Logger.Level.DEBUG, exception);
        }


    }

    public static int approximateTokens(String text) {
        return text.length() / 4;
    }
}
