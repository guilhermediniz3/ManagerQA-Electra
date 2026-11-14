package com.tester;
import com.tester.service.*;import com.tester.dto.TestCaseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
class CaseContentTest {
    JdbcTemplate db;CaseMediaService media;CaseContentService content;ObjectMapper mapper=new ObjectMapper();
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:content_"+java.util.UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));db.execute("CREATE SCHEMA operational");db.execute("CREATE TABLE operational.tb_test_case(id bigint PRIMARY KEY,expected_result text,obtained_result text)");db.update("INSERT INTO operational.tb_test_case(id) VALUES(1),(2)");
        var cases=mock(TestCaseService.class);var dto=mock(TestCaseDTO.class);when(dto.getExpectedResult()).thenReturn("Antes\nDepois");when(cases.findById(anyLong())).thenReturn(dto);media=new CaseMediaService(db,cases);media.initialize();content=new CaseContentService(db,media,cases,mapper);content.initialize();
    }
    @Test void legacyTextAndPrintAreMigratedWithoutChangingStoredText() throws Exception {
        var file=media.save(1,"expected",new MockMultipartFile("file","a.png","image/png",new byte[]{1}));var doc=content.read(1);
        assertThat(doc.path("expected")).hasSize(3);assertThat(doc.at("/expected/2/mediaId").asText()).isEqualTo(file.id().toString());
    }
    @Test void orderedContentRoundTripsAndDeletedMediaDoesNotDeleteText() throws Exception {
        var file=media.save(1,"expected",new MockMultipartFile("file","a.png","image/png",new byte[]{1}));
        var doc=mapper.createObjectNode();var expected=doc.putArray("expected");expected.addObject().put("type","text").put("text","Antes");expected.addObject().put("type","image").put("mediaId",file.id().toString());expected.addObject().put("type","text").put("text","Depois");doc.putArray("obtained");
        content.save(1,doc);assertThat(content.read(1)).isEqualTo(doc);assertThat(db.queryForObject("SELECT expected_result FROM operational.tb_test_case WHERE id=1",String.class)).isEqualTo("Antes\nDepois");
        media.clear();assertThat(content.read(1).path("expected")).hasSize(2);assertThat(content.read(1).at("/expected/1/text").asText()).isEqualTo("Depois");
    }
    @Test void refusesImagesFromAnotherCaseOrField() throws Exception {
        var file=media.save(2,"obtained",new MockMultipartFile("file","a.png","image/png",new byte[]{1}));var doc=mapper.createObjectNode();doc.putArray("expected").addObject().put("type","image").put("mediaId",file.id().toString());doc.putArray("obtained");
        assertThatThrownBy(()->content.save(1,doc)).hasMessageContaining("não pertence");assertThatThrownBy(()->content.save(2,doc)).hasMessageContaining("não pertence");
    }
}
