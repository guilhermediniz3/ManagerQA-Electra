package com.tester.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;

/** Ordered blocks, separate from the plain-text fields used by existing reports. */
@Service
public class CaseContentService {
    private final JdbcTemplate db;
    private final CaseMediaService media;
    private final TestCaseService cases;
    private final ObjectMapper mapper;
    public CaseContentService(JdbcTemplate db,CaseMediaService media,TestCaseService cases,ObjectMapper mapper){this.db=db;this.media=media;this.cases=cases;this.mapper=mapper;}
    @PostConstruct public void initialize(){db.execute("CREATE TABLE IF NOT EXISTS operational.case_content (case_id bigint PRIMARY KEY REFERENCES operational.tb_test_case(id) ON DELETE CASCADE, content text NOT NULL)");}
    public ObjectNode read(long id) {
        var c=cases.findById(id);
        var files=media.list(id);
        var rows=db.queryForList("SELECT content FROM operational.case_content WHERE case_id=?",String.class,id);
        if(!rows.isEmpty())try{
            var doc=(ObjectNode)mapper.readTree(rows.getFirst());
            for(String field:List.of("expected","obtained")) {
                var blocks=doc.withArray(field);
                for(int i=blocks.size()-1;i>=0;i--)if("image".equals(blocks.get(i).path("type").asText())) {
                    String mediaId=blocks.get(i).path("mediaId").asText();
                    if(files.stream().noneMatch(f->f.id().toString().equals(mediaId)))blocks.remove(i);
                }
            }
            return doc;
        }catch(java.io.IOException e){throw new IllegalStateException("Não foi possível ler o conteúdo do cenário.",e);}
        var doc=mapper.createObjectNode();
        for(String field:List.of("expected","obtained")) {
            var blocks=doc.putArray(field);String text=field.equals("expected")?c.getExpectedResult():c.getObtainedResult();
            if(text!=null)for(String line:text.split("\\R",-1))blocks.addObject().put("type","text").put("text",line);
            for(var file:files)if(file.field().equals(field))blocks.addObject().put("type","image").put("mediaId",file.id().toString());
        }
        return doc;
    }
    @org.springframework.transaction.annotation.Transactional
    public ObjectNode save(long id,JsonNode input) {
        cases.findById(id);var files=media.list(id);var clean=mapper.createObjectNode();
        if(input==null || !input.isObject() || input.toString().length()>100000)throw new IllegalArgumentException("Conteúdo inválido ou muito grande.");
        for(String field:List.of("expected","obtained")) {
            var nodes=input.path(field);if(!nodes.isArray() || nodes.size()>1000)throw new IllegalArgumentException("Blocos de resultado inválidos.");
            var blocks=clean.putArray(field);
            for(var node:nodes) {
                switch(node.path("type").asText()) {
                    case "text" -> {if(!node.path("text").isTextual())throw new IllegalArgumentException("Texto inválido.");blocks.addObject().put("type","text").put("text",node.path("text").asText());}
                    case "image" -> {
                        var file=files.stream().filter(f->f.field().equals(field) && f.mime().startsWith("image/") && f.id().toString().equals(node.path("mediaId").asText())).findFirst().orElseThrow(()->new IllegalArgumentException("O print não pertence a este resultado do cenário."));
                        blocks.addObject().put("type","image").put("mediaId",file.id().toString());
                    }
                    default -> throw new IllegalArgumentException("Tipo de conteúdo inválido.");
                }
            }
        }
        db.update("DELETE FROM operational.case_content WHERE case_id=?",id);
        db.update("INSERT INTO operational.case_content(case_id,content) VALUES (?,?)",id,clean.toString());
        db.update("UPDATE operational.tb_test_case SET expected_result=?,obtained_result=? WHERE id=?",plain(clean.path("expected")),plain(clean.path("obtained")),id);
        return clean;
    }
    private String plain(JsonNode blocks){var lines=new ArrayList<String>();for(var block:blocks)if("text".equals(block.path("type").asText()))lines.add(block.path("text").asText());return String.join("\n",lines);}
}
