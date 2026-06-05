package com.v1hz.acpagent;

import com.agentclientprotocol.sdk.agent.PromptContext;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.v1hz.acpagent.constants.AcpConstants;
import com.v1hz.acpagent.constants.enums.SessionStatusEnum;
import com.v1hz.acpagent.entity.Session;
import com.v1hz.acpagent.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AcpAgent {

    private final ChatService chatService;
    private final SessionService sessionService;
    private final AgentService agentService;
    private final SessionUpdateService sessionUpdateService;
    private final CancellationService cancellationService;
    private final McpService mcpService;

    /**
     * 按 sessionId 追踪 prompt 取消信号
     */
    private final ConcurrentHashMap<String, Sinks.One<StopReason>> cancelSignals = new ConcurrentHashMap<>();

    public AcpAgent(
            ChatService chatService,
            SessionService sessionService,
            AgentService agentService,
            SessionUpdateService sessionUpdateService,
            CancellationService cancellationService,
            McpService mcpService
    ) {
        this.chatService = chatService;
        this.sessionService = sessionService;
        this.agentService = agentService;
        this.sessionUpdateService = sessionUpdateService;
        this.cancellationService = cancellationService;
        this.mcpService = mcpService;
    }

    public Mono<InitializeResponse> init(InitializeRequest request) {
        log.info("收到ACP请求：{}", request);
        var caps = new AgentCapabilities(
                true,
                new SessionCapabilities(Map.of(), Map.of(), Map.of(), null),
                new McpCapabilities(false, false),
                new PromptCapabilities(false, false, false),
                null
        );
        return Mono.just(new InitializeResponse(
                1, caps, null,
                new Implementation("Agent", "1.0.0", "AI智能助手"),
                null
        ));
    }

    public Mono<NewSessionResponse> newSession(NewSessionRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromCallable(() -> {
            String sessionId = sessionService.create(request.cwd());
            mcpService.initClients(sessionId, request.mcpServers());
            return new NewSessionResponse(
                    sessionId,
                    new SessionModeState(AcpConstants.DEFAULT_SESSION_MODE_ID, AcpConstants.MODES),
                    new SessionModelState(AcpConstants.DEFAULT_SESSION_MODEL_ID, AcpConstants.MODELS),
                    null
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<LoadSessionResponse> loadSession(LoadSessionRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromCallable(() -> {
            String sessionId = request.sessionId();
            Session session = sessionService.findById(sessionId).orElseThrow();
            mcpService.initClients(sessionId, request.mcpServers());
            var response = new LoadSessionResponse(
                    new SessionModeState(session.getModeId(), AcpConstants.MODES),
                    new SessionModelState(session.getModelId(), AcpConstants.MODELS),
                    null
            );
            // 回放历史消息
            Collection<Checkpoint> checkpoints = agentService.getSessionCheckpoints(sessionId);
            if (checkpoints.isEmpty()) return response;
            Checkpoint latest = checkpoints.iterator().next();
            Object msgs = latest.getState().get("messages");
            if (msgs instanceof List<?> msgList) {
                sessionUpdateService.replayMessages(sessionId, msgList);
            }
            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ResumeSessionResponse> resumeSession(ResumeSessionRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromCallable(() -> {
            String sessionId = request.sessionId();
            Session session = sessionService.findById(sessionId).orElseThrow();
            mcpService.initClients(sessionId, request.mcpServers());
            return new ResumeSessionResponse(
                    new SessionModeState(session.getModeId(), AcpConstants.MODES),
                    new SessionModelState(session.getModelId(), AcpConstants.MODELS),
                    null
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<CloseSessionResponse> closeSession(CloseSessionRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromRunnable(() -> {
                    String sessionId = request.sessionId();
                    cancelSession(sessionId);
                    mcpService.closeClients(sessionId);
                    sessionService.close(sessionId);
                }).thenReturn(new CloseSessionResponse(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ListSessionsResponse> listSessions(ListSessionsRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromCallable(() -> {
            List<Session> sessions = sessionService.list(request.cwd());
            List<SessionInfo> infos = sessions.stream()
                    .filter(s -> SessionStatusEnum.ACTIVE.equals(s.getStatus()))
                    .map(s -> new SessionInfo(
                            s.getSessionId(), s.getTitle(), s.getCwd(),
                            s.getUpdatedAt().toString(), null
                    )).toList();
            return new ListSessionsResponse(infos, null, null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<SetSessionModeResponse> setSessionMode(SetSessionModeRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromRunnable(() ->
                        sessionService.setMode(request.sessionId(), request.modeId())
                ).thenReturn(new SetSessionModeResponse())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<SetSessionModelResponse> setSessionModel(SetSessionModelRequest request) {
        log.info("收到ACP请求：{}", request);
        return Mono.fromRunnable(() ->
                        sessionService.setModel(request.sessionId(), request.modelId())
                ).thenReturn(new SetSessionModelResponse())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> cancel(CancelNotification notification) {
        log.info("收到取消对话通知：{}", notification);
        String sessionId = notification.sessionId();
        cancellationService.cancel(sessionId);
        cancelSession(sessionId);
        return Mono.empty();
    }

    public Mono<PromptResponse> prompt(PromptRequest request, PromptContext context) {
        log.info("收到ACP请求：{}", request);

        String sessionId = request.sessionId();
        String message = request.text();
        List<Media> media = new ArrayList<>(); // TODO 根据prompt构建media

        Sinks.One<StopReason> cancelSignal = Sinks.one();
        cancelSignals.put(sessionId, cancelSignal);

        return chatService.chatStreamAcp(message, media, sessionId)
                .takeUntilOther(cancelSignal.asMono())
                .flatMap(output -> {
                    if (!(output instanceof StreamingOutput<?> so)) {
                        return Mono.empty();
                    }
                    if (!so.getOutputType().equals(OutputType.AGENT_MODEL_STREAMING)) {
                        return Mono.empty();
                    }
                    List<Mono<Void>> actions = new ArrayList<>();
                    // 尝试获取思考块
                    if (!(so.message() instanceof DeepSeekAssistantMessage ds))
                        return Mono.empty();
                    String thinkChunk = ds.getReasoningContent();
                    if (StringUtils.isNotEmpty(thinkChunk) && !ds.hasToolCalls()) {
                        actions.add(context.sendThought(thinkChunk));
                    }
                    // 尝试获取回答块
                    String answerChunk = so.message().getText();
                    //! 这里不能用isNotBlank，要完整保留换行符和空格等字符
                    if (StringUtils.isNotEmpty(answerChunk)) {
                        actions.add(context.sendMessage(answerChunk));
                    }
                    // 工具调用通知已由 AcpPermissionToolInterceptor 统一管理
                    return actions.isEmpty() ? Mono.empty() : Mono.when(actions);
                })
                .then(Mono.defer(() -> Mono.firstWithValue(
                        cancelSignal.asMono(),
                        Mono.just(StopReason.END_TURN)
                )))
                .map(PromptResponse::new)
                .doFinally(sig -> {
                    cancelSignals.remove(sessionId);
                    cancellationService.clear(sessionId);
                });
    }

    private void cancelSession(String sessionId) {
        var signal = cancelSignals.get(sessionId);
        if (signal != null) {
            signal.tryEmitValue(StopReason.CANCELLED);
        }
    }
}
