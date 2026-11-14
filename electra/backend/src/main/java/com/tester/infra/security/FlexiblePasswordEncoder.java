package com.tester.infra.security;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Retains existing BCrypt credentials and stores long passwords without truncation. */
public class FlexiblePasswordEncoder implements PasswordEncoder {
    private static final String PREFIX="{pbkdf2}";
    private final PasswordEncoder legacy=new BCryptPasswordEncoder();
    private final PasswordEncoder longPasswords=Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    @Override public String encode(CharSequence raw) {
        if(raw==null)throw new IllegalArgumentException("Informe a senha.");
        return raw.toString().getBytes(StandardCharsets.UTF_8).length>72
            ? PREFIX+longPasswords.encode(raw) : legacy.encode(raw);
    }
    @Override public boolean matches(CharSequence raw,String encoded) {
        if(raw==null || encoded==null)return false;
        if(encoded.startsWith(PREFIX))return longPasswords.matches(raw,encoded.substring(PREFIX.length()));
        if(raw.toString().getBytes(StandardCharsets.UTF_8).length>72)return false;
        return legacy.matches(raw,encoded);
    }
}
