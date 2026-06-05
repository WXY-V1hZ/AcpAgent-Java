package com.v1hz.acpagent.service;

import com.agentclientprotocol.sdk.spec.AcpSchema.McpServer;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerStdio;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerHttp;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServerSse;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class McpService {

    private final Map<String, List<McpSyncClient>> sessionClients = new ConcurrentHashMap<>();

    /**
     * 为指定 session 初始化 MCP 客户端连接。传入 null 或空列表时跳过。
     */
    public synchronized void initClients(String sessionId, @Nullable List<McpServer> servers) {
        closeClients(sessionId);
        if (servers == null || servers.isEmpty()) return;

        List<McpSyncClient> clients = new ArrayList<>();
        for (McpServer server : servers) {
            try {
                McpClientTransport transport = createTransport(server);
                if (transport == null) continue;
                var client = McpClient.sync(transport)
                        .clientInfo(new McpSchema.Implementation("AcpAgent", "1.0.0"))
                        .build();
                clients.add(client);
            } catch (Exception e) {
                log.warn("Failed to create MCP client: {}", e.getMessage());
            }
        }

        if (!clients.isEmpty()) {
            sessionClients.put(sessionId, clients);
            log.info("Initialized {} MCP client(s) for session {}", clients.size(), sessionId);
        }
    }

    /**
     * 获取指定 session 的 MCP 工具回调列表。
     */
    public List<ToolCallback> getToolCallbacks(String sessionId) {
        List<McpSyncClient> clients = sessionClients.get(sessionId);
        if (clients == null || clients.isEmpty()) return List.of();
        return SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
    }

    /**
     * 关闭指定 session 的所有 MCP 客户端连接。
     */
    public synchronized void closeClients(String sessionId) {
        List<McpSyncClient> clients = sessionClients.remove(sessionId);
        if (clients != null) {
            for (McpSyncClient client : clients) {
                try { client.closeGracefully(); }
                catch (Exception e) { log.warn("Error closing MCP client: {}", e.getMessage()); }
            }
        }
    }

    @PreDestroy
    void shutdownAll() {
        for (var entry : sessionClients.entrySet()) {
            closeClients(entry.getKey());
        }
    }

    // ─── 创建传输层 ───

    private McpClientTransport createTransport(McpServer server) {
        if (server instanceof McpServerStdio s) {
            var params = ServerParameters.builder(s.command())
                    .args(s.args() != null ? s.args() : List.of())
                    .build();
            if (s.env() != null) {
                for (var e : s.env()) {
                    if (e.name() != null) params.getEnv().put(e.name(), e.value());
                }
            }
            return new StdioClientTransport(params, new JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper()));
        }
        if (server instanceof McpServerHttp h) {
            return HttpClientSseClientTransport.builder(h.url()).build();
        }
        if (server instanceof McpServerSse s) {
            return HttpClientSseClientTransport.builder(s.url()).build();
        }
        log.warn("Unknown MCP server type: {}", server.getClass().getSimpleName());
        return null;
    }
}
