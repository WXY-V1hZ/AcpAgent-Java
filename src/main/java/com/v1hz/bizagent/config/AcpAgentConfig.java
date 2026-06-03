package com.v1hz.bizagent.config;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.agent.transport.WebSocketAcpAgentTransport;
import com.agentclientprotocol.sdk.json.JacksonAcpJsonMapper;
import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1hz.bizagent.BizAcpAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AcpAgentConfig {

    @Value("${acp.ws-port:8081}")
    private int wsPort;

    @Bean
    public AcpAgentTransport acpAgentTransport(ApplicationArguments args) {
        if (args.containsOption("acp")) {
            log.info("Starting ACP stdio transport");
            return new StdioAcpAgentTransport();
        }
        log.info("Starting ACP WebSocket transport on port {}", wsPort);
        return new WebSocketAcpAgentTransport(wsPort, "/acp", new JacksonAcpJsonMapper(new ObjectMapper()));
    }

    @Bean
    public AcpAsyncAgent acpAgent(
            AcpAgentTransport transport,
            BizAcpAgent handlers
    ) {
        return AcpAgent.async(transport)
                .initializeHandler(handlers::init)
                .newSessionHandler(handlers::newSession)
                .loadSessionHandler(handlers::loadSession)
                .resumeSessionHandler(handlers::resumeSession)
                .closeSessionHandler(handlers::closeSession)
                .listSessionsHandler(handlers::listSessions)
                .setSessionModeHandler(handlers::setSessionMode)
                .setSessionModelHandler(handlers::setSessionModel)
                .promptHandler(handlers::prompt)
                .cancelHandler(handlers::cancel)
                .build();
    }

    @Bean
    CommandLineRunner agentRunner(AcpAsyncAgent agent, ApplicationArguments args) {
        return ignored -> {
            agent.start().block();
            log.info("ACP agent started");
            // stdio 模式下阻塞主线程，等待 client 断开
            if (args.containsOption("acp")) {
                agent.awaitTermination().block();
            }
        };
    }
}
