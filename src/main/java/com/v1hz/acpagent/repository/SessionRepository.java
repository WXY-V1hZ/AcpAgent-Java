package com.v1hz.acpagent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.v1hz.acpagent.entity.Session;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.Nullable;

@Slf4j
@Repository
public class SessionRepository {

    private static final TypeReference<List<SessionIndexItem>> INDEX_TYPE = new TypeReference<>() {};

    private final Path storeDir;
    private final Path indexFile;
    private final ObjectMapper mapper;
    private final String indexFileName;

    public SessionRepository(
            @Value("${acpagent.home-dir}") String homeDir,
            @Value("${acpagent.session.session-dir}") String sessionDir,
            @Value("${acpagent.session.index-file-name}") String indexFileName
    ) {
        this.indexFileName = indexFileName;
        this.storeDir = Path.of(System.getProperty("user.home"), homeDir, sessionDir);
        this.indexFile = storeDir.resolve(indexFileName);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storeDir);
            log.info("Session store initialized at {}", storeDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session store directory", e);
        }
    }

    public void save(Session session) {
        try {
            File file = sessionFile(session.getSessionId());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, session);
            updateIndex(session);
            log.debug("Session saved: {}", session.getSessionId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session: " + session.getSessionId(), e);
        }
    }

    public Optional<Session> findById(String sessionId) {
        File file = sessionFile(sessionId);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file, Session.class));
        } catch (IOException e) {
            log.error("Failed to read session: {}", sessionId, e);
            return Optional.empty();
        }
    }

    public void deleteById(String sessionId) {
        File file = sessionFile(sessionId);
        if (file.delete()) {
            rebuildIndex();
            log.debug("Session deleted: {}", sessionId);
        }
    }

    private File sessionFile(String sessionId) {
        return storeDir.resolve(sessionId + ".json").toFile();
    }

    // ========== 索引管理 ==========

    private record SessionIndexItem(String sessionId, String title, String cwd, String updatedAt) {}

    private synchronized void updateIndex(Session session) {
        List<SessionIndexItem> index = readIndex();
        // 替换或新增
        index.removeIf(item -> item.sessionId().equals(session.getSessionId()));
        index.add(new SessionIndexItem(
                session.getSessionId(),
                session.getTitle(),
                session.getCwd(),
                session.getUpdatedAt().toString()
        ));
        writeIndex(index);
    }

    private synchronized void rebuildIndex() {
        File[] files = storeDir.toFile().listFiles((dir, name) -> name.endsWith(".json") && !name.equals(indexFileName));
        List<SessionIndexItem> index = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                String id = file.getName().replace(".json", "");
                findById(id).ifPresent(s -> index.add(new SessionIndexItem(
                        s.getSessionId(), s.getTitle(), s.getCwd(), s.getUpdatedAt().toString()
                )));
            }
        }
        writeIndex(index);
    }

    /**
     * 按 cwd 过滤并读取完整 Session。
     * cwd 为 null 时返回全部。
     */
    public List<Session> list(@Nullable String cwd) {
        List<SessionIndexItem> index = readIndex();
        return index.stream()
                .filter(item -> cwd == null || cwd.equals(item.cwd()))
                .flatMap(item -> findById(item.sessionId()).stream())
                .toList();
    }

    private List<SessionIndexItem> readIndex() {
        if (!indexFile.toFile().exists()) {
            return new ArrayList<>();
        }
        try {
            List<SessionIndexItem> items = mapper.readValue(indexFile.toFile(), INDEX_TYPE);
            return items != null ? items : new ArrayList<>();
        } catch (IOException e) {
            log.warn("Failed to read session index, rebuilding", e);
            return new ArrayList<>();
        }
    }

    private void writeIndex(List<SessionIndexItem> index) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            log.error("Failed to write session index", e);
        }
    }
}
