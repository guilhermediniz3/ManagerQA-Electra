package com.tester.infra;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * O app desktop abre a interface servida por este backend. Recarregar uma rota
 * como /test-plans/3 pediria um arquivo que não existe, então caminhos sem
 * arquivo correspondente caem no index.html e o React Router resolve a rota.
 * Endpoints REST continuam tendo precedência: controladores são resolvidos antes
 * dos handlers de recurso estático.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String path, Resource location) throws IOException {
                    Resource requested = location.createRelative(path);
                    if (requested.exists() && requested.isReadable()) return requested;
                    // Chamada de API que chegou até aqui é rota inexistente, e
                    // precisa continuar sendo 404 — devolver a página faria um
                    // endpoint errado parecer sucesso. A lista é a mesma que
                    // decide o acesso; manter duas deixou uma para trás, e
                    // caminhos como /runmanager/errado voltavam com a página.
                    if (com.tester.infra.security.RotaDeTela.daApi(path)) return null;
                    Resource index = new ClassPathResource("/static/index.html");
                    return index.exists() ? index : null;
                }
            });
    }
}
