package com.bluemoon.controllers;

import com.bluemoon.config.*;
import com.bluemoon.dao.ResidentDAO;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** 600 owned apartments P9000..P9599, 2396 residents, one empty apartment. No table DROP. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
class ResidentSearchMySqlIT {
    private static DatabaseConnection connections;
    private ResidentSearchService service;
    private Stage stage;
    private ResidentSearchController controller;
    @BeforeAll static void database() throws Exception {
        connections=new DatabaseConnection(DatabaseConfig.load());
        try(var c=connections.getConnection()){safe(c);}
        for(String file:new String[]{"us001_household_apartment.sql","us002_residents.sql"}) {
            String script=Files.readString(Path.of("database/migrations",file)).replaceAll("(?m)^\\s*--.*$","");
            try(var c=connections.getConnection()){safe(c);for(String command:script.split(";"))if(!command.isBlank())try(var s=c.prepareStatement(command)){s.execute();}}
        }
        cleanup();
        try(var c=connections.getConnection()) {
            safe(c);c.setAutoCommit(false);
            try(var apartments=c.prepareStatement("INSERT INTO apartments(so_can_ho,tang,dien_tich,trang_thai) VALUES (?,?,80,'DANG_O')");
                var homes=c.prepareStatement("INSERT INTO households(ma_ho,so_can_ho,ngay_nhan_nha) VALUES (?,?,'2024-01-01')");
                var residents=c.prepareStatement("INSERT INTO residents(ma_ho,ho_ten,ngay_sinh,gioi_tinh,so_cccd,quan_he_chu_ho,ngay_chuyen_den) VALUES (?,?,'1990-01-01','NAM',?,?,'2024-01-01')")) {
                for(int i=0;i<600;i++) {
                    apartments.setString(1,"P"+(9000+i));apartments.setInt(2,i/20+1);apartments.addBatch();
                    homes.setString(1,"SEARCH_IT_"+i);homes.setString(2,"P"+(9000+i));homes.addBatch();
                    if(i==599)continue;
                    for(int j=0;j<4;j++) {
                        residents.setString(1,"SEARCH_IT_"+i);residents.setString(2,j==0?"Đỗ Gia Ngọc":"Nguyễn Văn An");
                        residents.setString(3,String.format(Locale.ROOT,"914%09d",i*4+j+1));residents.setString(4,j==0?"CHU_HO":"CON");residents.addBatch();
                    }
                }
                apartments.executeBatch();homes.executeBatch();residents.executeBatch();c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
        var ready=new CompletableFuture<Void>();try{Platform.startup(() -> {Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException started){ready.complete(null);}ready.get(10,TimeUnit.SECONDS);
    }
    private static void safe(java.sql.Connection c) throws Exception {assertNotNull(c.getCatalog());assertTrue(c.getCatalog().endsWith("_test"));assertEquals("MySQL",c.getMetaData().getDatabaseProductName());}
    @BeforeEach void service(){FeeCategorySessionFixture.use(Role.ADMIN);service=new ResidentSearchServiceImpl(new ResidentDAO(connections),new RoleBasedAuthorizationService(null));}
    @AfterEach void close() throws Exception {if(stage!=null)fx(() -> {controller.close();stage.close();});FeeCategorySessionFixture.use(null);}
    @AfterAll static void cleanup() throws Exception {
        if(connections==null)return;
        try(var c=connections.getConnection()) {
            safe(c);var apartments=new ArrayList<String>();
            try(var s=c.prepareStatement("SELECT so_can_ho FROM households WHERE ma_ho LIKE 'SEARCH!_IT!_%' ESCAPE '!'");var r=s.executeQuery()){while(r.next())apartments.add(r.getString(1));}
            c.setAutoCommit(false);
            try {
                for(String table:List.of("residents","households"))try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE ma_ho LIKE 'SEARCH!_IT!_%' ESCAPE '!'")){s.executeUpdate();}
                try(var s=c.prepareStatement("DELETE FROM apartments WHERE so_can_ho=?")){for(String code:apartments){s.setString(1,code);s.addBatch();}s.executeBatch();}c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Test void apartmentPrefixAndEmptyApartmentIncludeHouseholdAndHead() {
        var result=service.search(ResidentSearchCriteria.Field.APARTMENT,"9000");assertTrue(result.isSuccess());var hits=result.data().orElseThrow();assertEquals(4,hits.size());assertEquals("Đỗ Gia Ngọc",hits.get(0).tenChuHo());assertEquals("SEARCH_IT_0",hits.get(0).maHo());
        assertEquals(400,service.search(ResidentSearchCriteria.Field.APARTMENT,"P90").data().orElseThrow().size());
        var empty=service.search(ResidentSearchCriteria.Field.APARTMENT,"9599").data().orElseThrow();assertEquals(1,empty.size());assertNull(empty.get(0).resident());
    }
    @Test void accentsCaseCccdFloorAndWildcardLiterals() {
        for(String name:List.of("ngoc","NgỌc","Đỗ Gia Ngọc","do gia ngoc"))assertEquals(599,service.search(ResidentSearchCriteria.Field.NAME,name).data().orElseThrow().stream().filter(h -> h.maHo().startsWith("SEARCH_IT_")).count());
        assertEquals(1,service.search(ResidentSearchCriteria.Field.CCCD,"914000000001").data().orElseThrow().size());
        assertEquals(80,service.search(ResidentSearchCriteria.Field.FLOOR,"12").data().orElseThrow().stream().filter(h -> h.maHo().startsWith("SEARCH_IT_")).count());
        for(String text:List.of("%","_","' OR 1=1 --"))assertTrue(service.search(ResidentSearchCriteria.Field.NAME,text).data().orElseThrow().isEmpty());
        assertTrue(service.search(ResidentSearchCriteria.Field.APARTMENT,"P9999").data().orElseThrow().isEmpty());
    }
    @Test void serviceResponseBelowOneSecondAt600Apartments() {
        var durations=new ArrayList<Double>();
        for(var field:ResidentSearchCriteria.Field.values()) {
            String text=switch(field){case APARTMENT -> "90";case NAME -> "ngoc";case CCCD -> "914000000001";case FLOOR -> "12";};
            service.search(field,text);
            for(int i=0;i<5;i++) {long start=System.nanoTime();var result=service.search(field,text);double ms=(System.nanoTime()-start)/1_000_000.0;assertTrue(result.isSuccess());durations.add(ms);assertTrue(ms<1000,"BR-24 response exceeded 1s: "+ms);}
        }
        durations.sort(Double::compareTo);System.out.printf(Locale.ROOT,"US014 BR24: fixture_apartments=600 fixture_residents=2396 samples=%d p50_ms=%.3f max_ms=%.3f%n",durations.size(),durations.get(durations.size()/2),durations.get(durations.size()-1));
    }
    @Test void realUiEnterSearchEmptyMessageAndDetailNavigation() throws Exception {
        String[] selected={null};
        fx(() -> {controller=new ResidentSearchController(service,v -> {},key -> selected[0]=key,() -> {});stage=new Stage();stage.setScene(new Scene(controller.view(),1000,700));stage.show();});
        long start=System.nanoTime();fx(() -> {((TextField)stage.getScene().lookup("#searchKeyword")).setText("9000");((TextField)stage.getScene().lookup("#searchKeyword")).getOnAction().handle(new javafx.event.ActionEvent());});
        until(() -> ((TableView<?>)stage.getScene().lookup("#residentSearchResults")).getItems().size()==4);
        double uiMs=(System.nanoTime()-start)/1_000_000.0;assertTrue(uiMs<1000,"UI response exceeded 1s: "+uiMs);System.out.printf(Locale.ROOT,"US014 UI request_to_results_ms=%.3f%n",uiMs);
        fx(() -> {
            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var image=stage.getScene().snapshot(null);
            var bitmap=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            try{var path=Path.of("target","us014-previews","search.png");Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(bitmap,"png",path.toFile());}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        fx(() -> {((TableView<?>)stage.getScene().lookup("#residentSearchResults")).getSelectionModel().selectFirst();((Button)stage.getScene().lookup("#searchDetailButton")).fire();assertEquals("SEARCH_IT_0",selected[0]);((TextField)stage.getScene().lookup("#searchKeyword")).setText("P9999");((Button)stage.getScene().lookup("#residentSearchButton")).fire();});
        until(() -> ((Label)stage.getScene().lookup("#residentSearchMessage")).getText().equals("Không tìm thấy hồ sơ cư dân phù hợp với từ khóa!"));
    }
    @Test void deniedRoleCannotQueryEvenWithValidInput() {
        FeeCategorySessionFixture.use(Role.RESIDENT);assertThrows(AccessDeniedException.class,() -> service.search(ResidentSearchCriteria.Field.NAME,"ngoc"));
    }
    private static void fx(Runnable work) throws Exception {var done=new CompletableFuture<Void>();Platform.runLater(() -> {try{work.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(15,TimeUnit.SECONDS);}
    private void until(BooleanSupplier condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){boolean[]ok={false};fx(() -> ok[0]=condition.getAsBoolean());if(ok[0])return;Thread.sleep(10);}fail("Search UI timeout");}
}
