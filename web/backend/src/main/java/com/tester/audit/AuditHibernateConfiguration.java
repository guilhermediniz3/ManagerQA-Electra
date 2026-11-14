package com.tester.audit;

import java.util.ArrayList;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditHibernateConfiguration {
    @Bean
    HibernatePropertiesCustomizer auditHibernateProperties() {
        return properties -> {
            var integrators = new ArrayList<Integrator>();
            if (properties.get("hibernate.integrator_provider") instanceof IntegratorProvider existing) {
                integrators.addAll(existing.getIntegrators());
            }
            integrators.add(new Integrator() {
                @Override
                public void integrate(Metadata metadata, BootstrapContext bootstrap,
                                      SessionFactoryImplementor factory) {
                    var registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
                    var listener = new AuditMutationListener();
                    registry.appendListeners(EventType.POST_INSERT, listener);
                    registry.appendListeners(EventType.POST_UPDATE, listener);
                    registry.appendListeners(EventType.POST_DELETE, listener);
                    registry.appendListeners(EventType.POST_COLLECTION_RECREATE, listener);
                    registry.appendListeners(EventType.POST_COLLECTION_UPDATE, listener);
                    registry.appendListeners(EventType.POST_COLLECTION_REMOVE, listener);
                }
                @Override
                public void disintegrate(SessionFactoryImplementor factory,
                                         SessionFactoryServiceRegistry registry) {}
            });
            properties.put("hibernate.integrator_provider", (IntegratorProvider) () -> integrators);
        };
    }
}
