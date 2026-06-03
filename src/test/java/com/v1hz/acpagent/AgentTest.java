package com.v1hz.acpagent;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.v1hz.acpagent.component.DeepSeekChatModel;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

@Slf4j
public class AgentTest {

    String SYSTEM_PROMPT = """
        始终使用中文思考和回答
        """;

    @Data
    private static class WeatherForLocationToolRequest {

        @ToolParam(description = "The city name")
        private String city;
    }

    private static class WeatherForLocationTool
        implements
            BiFunction<WeatherForLocationToolRequest, ToolContext, String>
    {

        @Override
        public String apply(
            WeatherForLocationToolRequest request,
            ToolContext toolContext
        ) {
            return "It's always sunny in " + request.getCity() + "!";
        }
    }

    @Data
    private static class UserLocationToolRequest {

        @ToolParam(description = "User query")
        private String query;
    }

    // 用户位置工具 - 使用上下文
    private static class UserLocationTool
        implements BiFunction<UserLocationToolRequest, ToolContext, String>
    {

        @Override
        public String apply(
            UserLocationToolRequest request,
            ToolContext toolContext
        ) {
            // 从上下文中获取用户信息
            String userId = "";
            if (toolContext != null && toolContext.getContext() != null) {
                RunnableConfig runnableConfig = (RunnableConfig) toolContext
                    .getContext()
                    .get(AGENT_CONFIG_CONTEXT_KEY);
                Optional<Object> userIdObjOptional = runnableConfig.metadata(
                    "user_id"
                );
                if (userIdObjOptional.isPresent()) {
                    userId = (String) userIdObjOptional.get();
                }
            }
            return "1".equals(userId) ? "Florida" : "San Francisco";
        }
    }

    // 创建工具回调
    ToolCallback getWeatherTool = FunctionToolCallback.builder(
        "getWeatherForLocation",
        new WeatherForLocationTool()
    )
        .description("Get weather for a given city")
        .inputType(WeatherForLocationToolRequest.class)
        .build();

    ToolCallback getUserLocationTool = FunctionToolCallback.builder(
        "getUserLocation",
        new UserLocationTool()
    )
        .description("Retrieve user location based on user ID")
        .inputType(UserLocationToolRequest.class)
        .build();

    DeepSeekApi deepSeekApi = DeepSeekApi.builder()
        .apiKey("sk-bb9d2badf4124140bebe8a2a007812b0")
        .build();

    ChatModel deepseekChatModel = DeepSeekChatModel.builder()
        .deepSeekApi(deepSeekApi)
        .defaultOptions(
            DeepSeekChatOptions.builder().model("deepseek-v4-pro").build()
        )
        .build();

    OpenAiApi openAiApi = OpenAiApi.builder()
        .baseUrl("https://api.aigocode.com")
        .apiKey(
            "sk-bf230ef8079134416c892648aac7e86a419b790f55194a99163d2f62ea086a28"
        )
        .build();

    ChatModel openAiChatModel = OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(
            OpenAiChatOptions.builder()
                .model("gpt-5.4-mini")
                .temperature(0.5)
                .maxTokens(1000)
                .build()
        )
        .build();

    // 使用 Java 类定义响应格式
    @Data
    public static class ResponseFormat {

        // 一个双关语响应（始终必需）
        private String punnyResponse;

        // 如果可用的话，关于天气的任何有趣信息
        private String weatherConditions;
    }

    ReactAgent agent = ReactAgent.builder()
        .name("weather_pun_agent")
        // .model(openAiChatModel)
        .model(deepseekChatModel)
        .systemPrompt(SYSTEM_PROMPT)
        .tools(getUserLocationTool, getWeatherTool)
        .outputType(ResponseFormat.class)
        .saver(new MemorySaver())
        .build();

    // threadId 是给定对话的唯一标识符
    RunnableConfig runnableConfig = RunnableConfig.builder()
        .threadId("1")
        .addMetadata("user_id", "1")
        .build();

    @Test
    public void test() throws GraphRunnerException {
        // 第一次调用
        AssistantMessage response = agent.call(
            "今天天气怎么样？",
            runnableConfig
        );
        System.out.println(response.getText());

        // 注意我们可以使用相同的 threadId 继续对话
        response = agent.call("谢谢！", runnableConfig);
        System.out.println(response.getText());
    }

    @Test
    public void test1() throws GraphRunnerException {
        OverAllState state = agent
            .invoke("告诉我你的模型型号，然后告诉我你能做什么", runnableConfig)
            .orElseThrow();
        log.info("OverAllState:\n{}", state);
    }

    @Test
    public void streamTest() throws GraphRunnerException {
        Flux<NodeOutput> stream = agent.stream("今天天气怎么样");
        var thinkingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false
        );
        var responseStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false
        );
        stream
            .doOnNext(output -> {
                if (output instanceof StreamingOutput<?> streamingOutput) {
                    OutputType type = streamingOutput.getOutputType();
                    switch (type) {
                        case AGENT_MODEL_STREAMING -> {
                            var message = streamingOutput.message();
                            if (
                                message instanceof
                                    DeepSeekAssistantMessage deepSeekMsg
                            ) {
                                String reasoning =
                                    deepSeekMsg.getReasoningContent();
                                if (StringUtils.isNotBlank(reasoning)) {
                                    if (!thinkingStarted.getAndSet(true)) {
                                        System.out.print("\n思考: ");
                                    }
                                    System.out.print(reasoning);
                                }
                                String text = deepSeekMsg.getText();
                                if (StringUtils.isNotBlank(text)) {
                                    if (!responseStarted.getAndSet(true)) {
                                        System.out.print("\n回答: ");
                                    }
                                    System.out.print(text);
                                }
                            } else {
                                if (StringUtils.isNotBlank(message.getText())) {
                                    System.out.print(message.getText());
                                }
                            }
                        }
                        case AGENT_MODEL_FINISHED -> {
                            thinkingStarted.set(false);
                            responseStarted.set(false);
                            System.out.println();
                            var usage = streamingOutput.tokenUsage();
                            if (usage != null) {
                                System.out.printf(
                                    "用量: prompt=%d, completion=%d, total=%d%n",
                                    usage.getPromptTokens(),
                                    usage.getCompletionTokens(),
                                    usage.getTotalTokens()
                                );
                            }
                        }
                        case AGENT_TOOL_FINISHED -> {
                            var msg = streamingOutput.message();
                            if (msg instanceof ToolResponseMessage trm) {
                                trm.getResponses().forEach(r ->
                                    System.out.printf(
                                        "工具结果: %s%n",
                                        r.responseData()
                                    )
                                );
                            }
                        }
                        default -> throw new IllegalArgumentException(
                            "Unexpected value: " + type
                        );
                    }
                }
            })
            .doOnError(error -> {
                System.err.println("错误: " + error.getMessage());
                if (
                    error instanceof WebClientResponseException webClientError
                ) {
                    System.err.println(
                        "响应体: " + webClientError.getResponseBodyAsString()
                    );
                    System.err.println(
                        "状态码: " + webClientError.getStatusCode()
                    );
                    System.err.println(
                        "请求头: " + webClientError.getHeaders()
                    );
                }
            })
            .blockLast(); // ← 阻塞主线程，直到流完成
    }
}
