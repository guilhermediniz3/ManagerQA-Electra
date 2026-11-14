package com.tester.audit;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;
    public AuditLogService(AuditLogRepository repository) { this.repository = repository; }

    public record Entry(Long id, Long actorId, String actorName, String actorEmail, String action,
                        String entityType, String entityId, Instant occurredAt) {
        static Entry from(AuditLog log) {
            return new Entry(log.getId(), log.getActorId(), log.getActorName(), log.getActorEmail(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getOccurredAt());
        }
    }
    public record Result(List<Entry> content, long totalElements, int totalPages, int number, int size) {}

    @Transactional(readOnly = true)
    public Result list(int page, int size, String action, String entityType) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Página deve ser positiva ou zero; tamanho deve estar entre 1 e 100.");
        }
        Specification<AuditLog> filter = (root, query, cb) -> cb.conjunction();
        if (action != null && !action.isBlank()) {
            String normalized = action.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("CREATE", "UPDATE", "DELETE").contains(normalized)) {
                throw new IllegalArgumentException("Ação deve ser CREATE, UPDATE ou DELETE.");
            }
            filter = filter.and((root, query, cb) -> cb.equal(root.get("action"), normalized));
        }
        if (entityType != null && !entityType.isBlank()) {
            String type = entityType.trim();
            if (type.length() > 100) throw new IllegalArgumentException("Tipo de registro inválido.");
            filter = filter.and((root, query, cb) -> cb.equal(root.get("entityType"), type));
        }
        var result = repository.findAll(filter,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        return new Result(result.getContent().stream().map(Entry::from).toList(),
            result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
