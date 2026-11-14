package com.tester.infra.cors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration public class CorsConfig implements WebMvcConfigurer {
 private final String[] origins;
 public CorsConfig(@Value("${manager.cors.origins:http://localhost:5173,http://127.0.0.1:5173}") String[] origins){this.origins=origins;}
 public void addCorsMappings(CorsRegistry registry){registry.addMapping("/**").allowedOrigins(origins).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("Authorization","Content-Type").exposedHeaders("Content-Disposition").maxAge(3600);}
}
