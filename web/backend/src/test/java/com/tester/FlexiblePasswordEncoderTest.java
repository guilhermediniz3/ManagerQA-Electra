package com.tester;
import com.tester.infra.security.FlexiblePasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FlexiblePasswordEncoderTest {
    @Test void retainsExistingHashes(){
        var old=new BCryptPasswordEncoder().encode("legacy-password");var encoder=new FlexiblePasswordEncoder();
        assertThat(encoder.matches("legacy-password",old)).isTrue();assertThat(encoder.matches("wrong",old)).isFalse();
    }
    @Test void longPasswordsDoNotShareTheSameTruncatedPrefix(){
        var encoder=new FlexiblePasswordEncoder();String prefix="a".repeat(100);
        var hash=encoder.encode(prefix+"one");assertThat(encoder.matches(prefix+"one",hash)).isTrue();assertThat(encoder.matches(prefix+"two",hash)).isFalse();
    }
}
