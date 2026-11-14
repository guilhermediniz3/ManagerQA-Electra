package com.tester;
import com.tester.entity.User;
import com.tester.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ActiveProfiles("test") @AutoConfigureMockMvc
@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:local_password_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
class LocalPasswordResetTest {
    @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired PasswordEncoder passwords;@Autowired ObjectMapper mapper;
    User account(boolean active){return users.save(new User("Local reset","reset-"+java.util.UUID.randomUUID()+"@example.test",passwords.encode("old-password"),active));}
    String body(String email,String password,String confirmation)throws Exception{return mapper.writeValueAsString(java.util.Map.of("email",email,"password",password,"confirmation",confirmation));}
    @Test void localResetWorksWithoutSessionAndStoresOnlyHash() throws Exception {
        var user=account(true);
        mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body(user.getEmail(),"new-local-123","new-local-123"))).andExpect(status().isOk());
        var saved=users.findById(user.getId()).orElseThrow();assertThat(saved.getPassword()).isNotEqualTo("new-local-123");assertThat(passwords.matches("new-local-123",saved.getPassword())).isTrue();
        mvc.perform(post("/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email",user.getEmail(),"password","old-password")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email",user.getEmail(),"password","new-local-123")))).andExpect(status().isOk());
    }
    @Test void refusesMismatchedAndEmptyPasswordsWithoutChangingAccount() throws Exception {
        var user=account(true);
        for(var pair:new String[][]{{"abcdef","different"},{"",""}})mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body(user.getEmail(),pair[0],pair[1]))).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(user.getPassword());
    }
    @Test void remoteRequestsCannotResetEvenWithLocalHostHeader() throws Exception {
        var user=account(true);
        mvc.perform(post("/auth/reset-local-password").with(r->{r.setRemoteAddr("192.168.1.50");r.setServerName("localhost");return r;}).contentType("application/json").content(body(user.getEmail(),"abcdef","abcdef"))).andExpect(status().isForbidden());
        assertThat(users.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(user.getPassword());
    }
    @Test void proxyAndNonLocalHostAreRefused() throws Exception {
        var user=account(true);String data=body(user.getEmail(),"abcdef","abcdef");
        mvc.perform(post("/auth/reset-local-password").header("X-Forwarded-For","192.168.1.50").contentType("application/json").content(data)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/reset-local-password").header("Forwarded","for=192.168.1.50").contentType("application/json").content(data)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/reset-local-password").with(r->{r.setServerName("qa.example.test");return r;}).contentType("application/json").content(data)).andExpect(status().isForbidden());
    }
    @Test void missingAndInactiveAccountsAreNotCreatedOrReactivated() throws Exception {
        mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body("missing@example.test","abcdef","abcdef"))).andExpect(status().isBadRequest());
        var user=account(false);
        mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body(user.getEmail(),"abcdef","abcdef"))).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isFalse();
    }
    @Test void ipv6LoopbackCanReset() throws Exception {
        var user=account(true);
        mvc.perform(post("/auth/reset-local-password").with(r->{r.setRemoteAddr("::1");r.setServerName("[::1]");return r;}).contentType("application/json").content(body(user.getEmail(),"abcdef","abcdef"))).andExpect(status().isOk());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"1"," ","á🙂 !@#$%\"'\\", "teste123"})
    void acceptsFreelyChosenPassword(String password) throws Exception {
        var user=account(true);
        mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body(user.getEmail(),password,password))).andExpect(status().isOk());
        mvc.perform(post("/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email",user.getEmail(),"password",password)))).andExpect(status().isOk());
    }
    @Test void longUnicodePasswordIsStoredWithoutTruncation() throws Exception {
        var user=account(true);String password="🙂á".repeat(200)+" end ";
        mvc.perform(post("/auth/reset-local-password").contentType("application/json").content(body(user.getEmail(),password,password))).andExpect(status().isOk());
        var saved=users.findById(user.getId()).orElseThrow();assertThat(passwords.matches(password,saved.getPassword())).isTrue();assertThat(passwords.matches(password+"x",saved.getPassword())).isFalse();
        mvc.perform(post("/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email",user.getEmail(),"password",password)))).andExpect(status().isOk());
    }
    @Test void registrationAlsoAcceptsOneCharacter() throws Exception {
        var email="free-"+java.util.UUID.randomUUID()+"@example.test";
        mvc.perform(post("/auth/register").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("name","Local","email",email,"password","a")))).andExpect(status().isCreated());
    }
}
