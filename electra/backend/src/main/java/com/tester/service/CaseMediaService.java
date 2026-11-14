package com.tester.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

/** Binary storage is separate from case queries, so reports never load video bytes. */
@Service
public class CaseMediaService {
    private final JdbcTemplate db;
    private final TestCaseService cases;
    public CaseMediaService(JdbcTemplate db, TestCaseService cases) {this.db=db;this.cases=cases;}
    @PostConstruct public void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS operational.case_media (id uuid PRIMARY KEY, case_id bigint NOT NULL REFERENCES operational.tb_test_case(id) ON DELETE CASCADE, field varchar(20) NOT NULL, mime varchar(60) NOT NULL, name varchar(120) NOT NULL, data bytea NOT NULL)");
    }
    public record View(UUID id, long caseId, String field, String mime, String name, long size) {}
    public List<View> list(long id) {
        cases.findById(id);
        return db.query("SELECT id,case_id,field,mime,name,octet_length(data) FROM operational.case_media WHERE case_id=? ORDER BY name,id",(r,n)->new View(r.getObject(1,UUID.class),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6)),id);
    }
    @org.springframework.transaction.annotation.Transactional
    public View save(long caseId,String field,MultipartFile file) throws java.io.IOException {
        cases.findById(caseId);
        String mime=Objects.toString(file.getContentType(),"").split(";")[0];
        boolean video=field.equals("video") && Set.of("video/webm","video/mp4").contains(mime);
        boolean image=Set.of("expected","obtained").contains(field) && Set.of("image/png","image/jpeg","image/webp").contains(mime);
        if ((!video && !image) || file.isEmpty() || file.getSize()>(video?512L:10L)*1024*1024) throw new IllegalArgumentException("Arquivo inválido. Vídeo até 512 MB; print PNG/JPG/WebP até 10 MB.");
        UUID id=UUID.randomUUID();
        String ext=Map.of("video/webm","webm","video/mp4","mp4","image/png","png","image/jpeg","jpg","image/webp","webp").get(mime);
        String name="CT-"+caseId+"-"+field+"-"+id+"."+ext;
        // A failed upload leaves the previous recording intact (same transaction).
        if(video) db.update("DELETE FROM operational.case_media WHERE case_id=? AND field='video'",caseId);
        db.update("INSERT INTO operational.case_media(id,case_id,field,mime,name,data) VALUES (?,?,?,?,?,?)",id,caseId,field,mime,name,file.getBytes());
        return new View(id,caseId,field,mime,name,file.getSize());
    }
    public byte[] bytes(long caseId,UUID id) {cases.findById(caseId);return db.queryForObject("SELECT data FROM operational.case_media WHERE case_id=? AND id=?",byte[].class,caseId,id);}
    public void delete(long caseId,UUID id) {cases.findById(caseId);db.update("DELETE FROM operational.case_media WHERE case_id=? AND id=?",caseId,id);}
    public record ManagedView(UUID id,long caseId,String field,String mime,String name,long size,String scenario,Long suiteId,String plan) {}
    public record Library(List<ManagedView> items,long total,long bytes,int page,int size) {}
    public Library library(int page,int size) {
        if(page<0 || size<1 || size>48)throw new IllegalArgumentException("Paginação inválida.");
        long total=db.queryForObject("SELECT count(*) FROM operational.case_media",Long.class);
        long bytes=db.queryForObject("SELECT coalesce(sum(octet_length(data)),0) FROM operational.case_media",Long.class);
        var items=db.query("SELECT m.id,m.case_id,m.field,m.mime,m.name,octet_length(m.data),c.scenario,c.test_suite_id,p.name FROM operational.case_media m JOIN operational.tb_test_case c ON c.id=m.case_id LEFT JOIN operational.tb_test_suite s ON s.id=c.test_suite_id LEFT JOIN operational.tb_test_plan p ON p.id=s.test_plan_id ORDER BY m.case_id,m.id LIMIT ? OFFSET ?",
            (r,n)->new ManagedView(r.getObject(1,UUID.class),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7),r.getObject(8,Long.class),r.getString(9)),size,(long)page*size);
        return new Library(items,total,bytes,page,size);
    }
    public int clear() {return db.update("DELETE FROM operational.case_media");}
}
