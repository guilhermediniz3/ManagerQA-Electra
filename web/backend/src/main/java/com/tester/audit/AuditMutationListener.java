package com.tester.audit;

import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.tester.entity.User;
import jakarta.transaction.Synchronization;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Logs actual ORM writes on their existing JDBC transaction. A rollback therefore
 * removes both the mutation and its log. No entity state, request or secret is read.
 * Bulk SQL performed outside Hibernate entity events is deliberately outside scope.
 */
final class AuditMutationListener implements PostInsertEventListener, PostUpdateEventListener,
        PostDeleteEventListener, PostCollectionRecreateEventListener,
        PostCollectionUpdateEventListener, PostCollectionRemoveEventListener {
    private static final String INSERT = "insert into operational.manager_audit_log "
        + "(actor_id,actor_name,actor_email,action,entity_type,entity_id,occurred_at) values (?,?,?,?,?,?,?)";
    private record Key(String entityType, String entityId) {}
    private record Written(long id, String action) {}
    private record Actor(Long id, String name, String email) {}
    private final Map<EventSource, Map<Key, Written>> transactions = new ConcurrentHashMap<>();

    @Override public void onPostInsert(PostInsertEvent event) {
        record(event.getSession(), event.getPersister().getEntityName(), event.getId(), "CREATE");
    }
    @Override public void onPostUpdate(PostUpdateEvent event) {
        record(event.getSession(), event.getPersister().getEntityName(), event.getId(), "UPDATE");
    }
    @Override public void onPostDelete(PostDeleteEvent event) {
        record(event.getSession(), event.getPersister().getEntityName(), event.getId(), "DELETE");
    }
    @Override public void onPostRecreateCollection(PostCollectionRecreateEvent event) { collection(event); }
    @Override public void onPostUpdateCollection(PostCollectionUpdateEvent event) { collection(event); }
    @Override public void onPostRemoveCollection(PostCollectionRemoveEvent event) { collection(event); }
    @Override public boolean requiresPostCommitHandling(EntityPersister persister) { return false; }

    private void collection(AbstractCollectionEvent event) {
        record(event.getSession(), event.getAffectedOwnerEntityName(), event.getAffectedOwnerIdOrNull(), "UPDATE");
    }

    private void record(EventSource session, String entityName, Object entityId, String action) {
        if (entityName == null || !entityName.startsWith("com.tester.")
                || entityName.startsWith("com.tester.audit.") || entityId == null) return;
        Actor currentActor = actor();
        final Actor actor = currentActor == null ? new Actor(null, "Sistema / cadastro público", "") : currentActor;
        String type = entityName.substring(entityName.lastIndexOf('.') + 1);
        Key key = new Key(type, String.valueOf(entityId));
        Map<Key, Written> writes = transactions.computeIfAbsent(session, source -> {
            source.getTransaction().registerSynchronization(new Synchronization() {
                @Override public void beforeCompletion() {}
                @Override public void afterCompletion(int status) { transactions.remove(source); }
            });
            return new HashMap<>();
        });
        Written previous = writes.get(key);
        if (previous != null) {
            // Association events may precede deletion or follow creation. Keep one
            // meaningful action per record in a transaction, including cloned children.
            if (!"DELETE".equals(action) || "DELETE".equals(previous.action())) return;
            session.doWork(connection -> {
                try (var statement = connection.prepareStatement(
                        "update operational.manager_audit_log set action=? where id=?")) {
                    statement.setString(1, action);
                    statement.setLong(2, previous.id());
                    statement.executeUpdate();
                }
            });
            writes.put(key, new Written(previous.id(), action));
            return;
        }
        session.doWork(connection -> {
            try (var statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                if (actor.id() == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, actor.id());
                statement.setString(2, actor.name());
                statement.setString(3, actor.email());
                statement.setString(4, action);
                statement.setString(5, key.entityType());
                statement.setString(6, key.entityId());
                statement.setObject(7, OffsetDateTime.now(ZoneOffset.UTC));
                statement.executeUpdate();
                try (var ids = statement.getGeneratedKeys()) {
                    if (!ids.next()) throw new java.sql.SQLException("Audit insert returned no identifier");
                    writes.put(key, new Written(ids.getLong(1), action));
                }
            }
        });
    }

    private Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return null;
        if (authentication.getPrincipal() instanceof User user) {
            return new Actor(user.getId(), bounded(user.getName(), user.getEmail()), bounded(user.getEmail(), ""));
        }
        String name = bounded(authentication.getName(), "Usuário autenticado");
        return new Actor(null, name, name);
    }
    private static String bounded(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        if (result == null) result = "";
        return result.substring(0, Math.min(result.length(), 255));
    }
}
