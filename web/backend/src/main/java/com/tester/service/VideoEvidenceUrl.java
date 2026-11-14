package com.tester.service;
import java.net.URI;
import java.util.Set;
public final class VideoEvidenceUrl {
    private VideoEvidenceUrl() {}
    public static boolean valid(String value) {
        try {
            var u=URI.create(value);
            if(!"https".equals(u.getScheme()) || u.getUserInfo()!=null || u.getPort()!=-1) return false;
            if(Set.of("vimeo.com","www.vimeo.com","player.vimeo.com").contains(u.getHost()))
                return u.getPath().matches("/(video/|manage/videos/)?[0-9]+(/[A-Za-z0-9]+)?/?");
            if(!"drive.google.com".equals(u.getHost())) return false;
            if(u.getPath().matches("/file/d/[A-Za-z0-9_-]+(/(view|preview|edit))?/?")) return true;
            return Set.of("/open","/uc").contains(u.getPath()) && u.getRawQuery()!=null
                && java.util.regex.Pattern.compile("(^|&)id=[A-Za-z0-9_-]+(&|$)").matcher(u.getRawQuery()).find();
        }catch(Exception e){return false;}
    }
    public static void validate(String value){
        if(value!=null && !value.isBlank() && !valid(value))
            throw new IllegalArgumentException("Informe uma URL HTTPS de vídeo do Vimeo ou Google Drive.");
    }
}
