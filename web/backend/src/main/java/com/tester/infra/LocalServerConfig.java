package com.tester.infra;
import org.springframework.context.annotation.*;import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;import org.springframework.boot.web.server.WebServerFactoryCustomizer;
@Configuration public class LocalServerConfig {
 @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> localConnector(){return factory->factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");}
}
