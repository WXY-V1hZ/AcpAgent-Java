package com.v1hz.bizagent.service;

import com.v1hz.bizagent.constants.AcpConstants;
import com.v1hz.bizagent.constants.enums.SessionModeEnum;
import com.v1hz.bizagent.constants.enums.SessionModelEnum;
import com.v1hz.bizagent.constants.enums.SessionStatusEnum;
import com.v1hz.bizagent.entity.Session;
import com.v1hz.bizagent.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    @NonNull
    public String create(@NonNull String cwd) {
        String sessionId = UUID.randomUUID().toString();
        Session session = Session.builder()
                .sessionId(sessionId)
                .title(sessionId)
                .cwd(cwd)
                .status(SessionStatusEnum.ACTIVE)
                .modeId(AcpConstants.DEFAULT_SESSION_MODE_ID)
                .modelId(AcpConstants.DEFAULT_SESSION_MODEL_ID)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);
        return sessionId;
    }

    @NonNull
    public Optional<Session> findById(@NonNull String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    @NonNull
    public List<Session> list(@Nullable String cwd) {
        return sessionRepository.list(cwd);
    }

    public void close(@NonNull String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(SessionStatusEnum.CLOSED);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    public void setMode(@NonNull String sessionId, @NonNull String modeId) {
        if (!SessionModeEnum.isValid(modeId)) {
            throw new IllegalArgumentException("Invalid mode id: " + modeId);
        }
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setModeId(modeId);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    public void setModel(@NonNull String sessionId, @NonNull String modelId) {
        if (!SessionModelEnum.isValid(modelId)) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setModelId(modelId);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }
}
