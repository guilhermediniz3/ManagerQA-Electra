package com.tester.integration.drive;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** Blocking streaming transport: the video is never loaded into the Java heap. */
@Component
public class DriveHttp {
    private final ObjectMapper mapper;
    public DriveHttp(ObjectMapper mapper){this.mapper=mapper;}
    public record Reply(int status,JsonNode body,String location) {}
    public Reply request(String method,String url,String token,String type,String body){
        return send(method,URI.create(url),token,type,body==null?null:body.getBytes(StandardCharsets.UTF_8),null);
    }
    public Reply upload(String location,String token,Path file,String type){
        var u=URI.create(location);
        if(!"https".equals(u.getScheme()) || !"www.googleapis.com".equals(u.getHost()) || u.getUserInfo()!=null || u.getPort()!=-1 || !u.getPath().startsWith("/upload/drive/"))
            throw new IllegalStateException("O Google devolveu um endereço de envio inválido.");
        return send("PUT",u,token,type,null,file);
    }
    private Reply send(String method,URI uri,String token,String type,byte[] body,Path file){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(20000);connection.setReadTimeout(file==null?60000:30*60*1000);
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
            if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
            if(type!=null)connection.setRequestProperty("Content-Type",type);
            if(body!=null || file!=null){
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(file!=null?Files.size(file):body.length);
                try(var output=connection.getOutputStream()){
                    if(file!=null)Files.copy(file,output);else output.write(body);
                }
            }
            int status=connection.getResponseCode();
            String location=connection.getHeaderField("Location");
            try(var input=status>=400?connection.getErrorStream():connection.getInputStream()){
                byte[] response=input==null?new byte[0]:input.readNBytes(1024*1024);
                JsonNode json=response.length==0?mapper.createObjectNode():mapper.readTree(response);
                return new Reply(status,json,location);
            }
        }catch(Exception e){throw new IllegalStateException("Não foi possível comunicar com o Google Drive. Verifique a conexão e tente novamente.");}
        finally{if(connection!=null)connection.disconnect();}
    }
}