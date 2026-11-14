package com.tester;

import com.auth0.jwt.JWT;
import com.tester.controller.AuthController;
import com.tester.entity.User;
import com.tester.infra.security.TokenService;
import com.tester.repository.UserRepository;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRenewalTest {
    @Test void expiresInTwoHoursRegardlessOfComputerTimezone() {
        var original=TimeZone.getDefault();
        try {
            var service=new TokenService();ReflectionTestUtils.setField(service,"secret","fixture-secret");
            var user=new User("Tester","tester@example.test","hash",true);
            for(String timezone:new String[]{"UTC","America/Sao_Paulo","Asia/Tokyo"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(timezone));
                var before=Instant.now();var token=service.generationToken(user);
                assertThat(JWT.decode(token).getExpiresAtAsInstant()).isBetween(before.plusSeconds(7199),before.plusSeconds(7201));
                assertThat(service.validateToken(token)).isEqualTo(user.getEmail());
            }
        } finally {TimeZone.setDefault(original);}
    }
    @Test void renewsAuthenticatedActiveUserButRejectsAnonymousAndInactive() {
        var tokens=mock(TokenService.class);
        var controller=new AuthController(mock(UserRepository.class),mock(PasswordEncoder.class),tokens);
        var active=new User("Tester","tester@example.test","hash",true);
        when(tokens.generationToken(active)).thenReturn("renewed");
        assertThat(controller.refresh(active).getBody().token()).isEqualTo("renewed");
        assertThat(controller.refresh(null).getStatusCode().value()).isEqualTo(401);
        active.setActive(false);
        assertThat(controller.refresh(active).getStatusCode().value()).isEqualTo(401);
        verify(tokens,times(1)).generationToken(active);
    }
}
