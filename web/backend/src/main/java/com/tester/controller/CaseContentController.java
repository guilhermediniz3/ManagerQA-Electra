package com.tester.controller;
import com.tester.service.CaseContentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/cases/{id}/content")
public class CaseContentController {
    private final CaseContentService content;
    public CaseContentController(CaseContentService content){this.content=content;}
    @GetMapping public JsonNode get(@PathVariable long id){return content.read(id);}
    @PutMapping public JsonNode save(@PathVariable long id,@RequestBody JsonNode input){return content.save(id,input);}
}
