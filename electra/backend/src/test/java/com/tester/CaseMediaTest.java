package com.tester;
import com.tester.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseMediaTest {
    JdbcTemplate db;
    CaseMediaService media;
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:media_"+java.util.UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE SCHEMA operational");
        db.execute("CREATE TABLE operational.tb_test_plan(id bigint PRIMARY KEY,name varchar(80))");
        db.execute("CREATE TABLE operational.tb_test_suite(id bigint PRIMARY KEY,test_plan_id bigint)");
        db.execute("CREATE TABLE operational.tb_test_case(id bigint PRIMARY KEY,scenario varchar(255),test_suite_id bigint)");
        db.update("INSERT INTO operational.tb_test_plan VALUES(7,'Plano de teste')");
        db.update("INSERT INTO operational.tb_test_suite VALUES(8,7)");
        db.update("INSERT INTO operational.tb_test_case VALUES(1,'Login',8),(2,'Logout',8)");
        media=new CaseMediaService(db,mock(TestCaseService.class));media.initialize();media.initialize();
    }
    @Test void storesBinaryAndReplacesOnlyVideoInSameCase() throws Exception {
        var print=media.save(1,"expected",new MockMultipartFile("file","a.png","image/png",new byte[]{1,2,3}));
        media.save(1,"video",new MockMultipartFile("file","a.webm","video/webm",new byte[]{4,5}));
        var other=media.save(2,"video",new MockMultipartFile("file","b.webm","video/webm",new byte[]{6}));
        var video=media.save(1,"video",new MockMultipartFile("file","c.mp4","video/mp4",new byte[]{7,8}));
        assertThat(media.list(1)).hasSize(2);
        assertThat(media.bytes(1,print.id())).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(media.bytes(1,video.id())).containsExactly((byte)7,(byte)8);
        assertThat(media.list(2)).containsExactly(other);
        assertThatThrownBy(()->media.bytes(2,print.id())).isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
        db.update("DELETE FROM operational.tb_test_case WHERE id=1");assertThat(media.list(1)).isEmpty();
    }
    @Test void rejectsWrongFieldAndEmptyFilesWithoutLosingPreviousVideo() throws Exception {
        media.save(1,"video",new MockMultipartFile("file","a.webm","video/webm",new byte[]{1}));
        assertThatThrownBy(()->media.save(1,"video",new MockMultipartFile("file","a.html","text/html",new byte[]{1}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->media.save(1,"expected",new MockMultipartFile("file","a.png","image/png",new byte[]{}))).isInstanceOf(IllegalArgumentException.class);
        assertThat(media.list(1)).hasSize(1);
    }
    @Test void libraryPaginatesMetadataAndClearsOnlyMediaAfterConfirmation() throws Exception {
        var image=media.save(1,"expected",new MockMultipartFile("file","a.png","image/png",new byte[]{1,2,3}));
        media.save(2,"video",new MockMultipartFile("file","a.webm","video/webm",new byte[]{4,5}));
        var first=media.library(0,1);
        assertThat(first.total()).isEqualTo(2);assertThat(first.bytes()).isEqualTo(5);
        assertThat(first.items()).hasSize(1);assertThat(first.items().getFirst().scenario()).isEqualTo("Login");
        assertThat(first.items().getFirst().plan()).isEqualTo("Plano de teste");
        assertThat(media.library(1,1).items().getFirst().caseId()).isEqualTo(2);
        assertThatThrownBy(()->media.library(-1,12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->media.library(0,100)).isInstanceOf(IllegalArgumentException.class);
        var controller=new com.tester.controller.MediaLibraryController(media);
        assertThatThrownBy(()->controller.clear("yes")).isInstanceOf(IllegalArgumentException.class);
        assertThat(media.library(0,12).total()).isEqualTo(2);
        media.delete(1,image.id());assertThat(media.library(0,12).bytes()).isEqualTo(2);
        assertThat(controller.clear("LIMPAR").get("deleted")).isEqualTo(1);
        assertThat(media.library(0,12).total()).isZero();assertThat(media.library(0,12).bytes()).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM operational.tb_test_case",Long.class)).isEqualTo(2);
    }
}
