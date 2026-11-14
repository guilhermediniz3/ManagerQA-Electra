package com.tester.integration.config;
import jakarta.persistence.*;
@Converter public class TestCredentialConverter implements AttributeConverter<String,String> {
 private final SecretCipher cipher;public TestCredentialConverter(SecretCipher cipher){this.cipher=cipher;}
 public String convertToDatabaseColumn(String value){return value==null||value.isBlank()?value:"enc:v1:"+cipher.encrypt(value);}
 public String convertToEntityAttribute(String value){return value!=null&&value.startsWith("enc:v1:")?cipher.decrypt(value.substring(7)):value;}
}
