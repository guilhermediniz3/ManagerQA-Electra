package com.tester.integration.config;
import java.util.*;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component public class SecretCipher {
 private final String master;
 public SecretCipher(@Value("${manager.integrations.master-key:}") String master){this.master=master;}
 private SecretKeySpec key(){
  try { byte[] bytes=Base64.getDecoder().decode(master); if(bytes.length!=32) throw new IllegalArgumentException(); return new SecretKeySpec(bytes,"AES"); }
  catch(IllegalArgumentException e){throw new IllegalStateException("Configure INTEGRATION_MASTER_KEY com 32 bytes em Base64.");}
 }
 public String encrypt(String value){
  try {byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
  catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Não foi possível proteger a credencial.");}
 }
 public String decrypt(String value){
  try {String[] parts=value.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}
  catch(Exception e){throw new IllegalStateException("Não foi possível abrir a credencial. Verifique a chave mestra.");}
 }
}
