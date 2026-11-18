package com.example.songbook.audit;

import com.example.songbook.common.response.PageResponse;
import com.example.songbook.common.util.RequestUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes audit records in the caller's transaction, so an audit entry exists if and only if the
 * audited change was committed. Use {@link #recordIndependently} for events that must survive a rollback
 * (e.g. failed logins).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        repository.save(new AuditLog(actorId, action, entityType, entityId, copy(details),
                RequestUtils.clientIp(), Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        record(actorId, action, entityType, entityId, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorId, String action, String entityType, UUID entityId,
                                                 Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (actorId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("actorUserId"), actorId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action.trim()));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), entityType.trim()));
        }
        if (entityId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("entityId"), entityId));
        }
        if (from != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), to));
        }
        return PageResponse.from(repository.findAll(spec, pageable), AuditLogResponse::from);
    }

    private static Map<String, Object> copy(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        details.forEach((k, v) -> copy.put(k, v == null ? null : String.valueOf(v)));
        return copy;
    }

    public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
                                   Map<String, Object> details, String ipAddress, Instant createdAt) {
        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getEntityType(),
                    log.getEntityId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
        }
    }
}
