package com.bluemoon.controllers;

import com.bluemoon.dao.*;
import com.bluemoon.models.*;
import com.bluemoon.services.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ResidentPanelUiTest {
    protected static final String HOUSE="UI_US002_2801";
    protected HouseholdRepository households;
    protected ResidentRepository residents;
    protected ResidentPanelController panel;
    protected Stage stage;
    protected boolean busy;
    @BeforeAll static void toolkit() throws Exception {
        var ready=new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch(IllegalStateException started) { ready.complete(null); } ready.get(10,TimeUnit.SECONDS);
    }
    protected void repositories() throws Exception {
        households=new InMemoryHouseholdRepository(); residents=new InMemoryResidentRepository(households);
    }
    @BeforeEach void setup() throws Exception {
        FeeCategorySessionFixture.use(Role.ADMIN); repositories();
        households.insert(new HouseholdProfile(new Apartment("P2801",28,new BigDecimal("80.00"),ApartmentStatus.DANG_O),
            new Household(HOUSE,"P2801",LocalDate.of(2024,1,1))));
        fx(() -> {
            panel=new ResidentPanelController(new ResidentServiceImpl(residents,households,new RoleBasedAuthorizationService(null)),HOUSE,v -> busy=v);
            stage=new Stage(); stage.setScene(new Scene(panel.view(),1000,700)); stage.show();
        }); until(() -> !busy);
    }
    @AfterEach void teardown() throws Exception {
        fx(() -> { panel.close(); stage.close(); }); FeeCategorySessionFixture.use(null); cleanup();
    }
    protected void cleanup() throws Exception { }
    protected static void fx(Runnable work) throws Exception {
        var done=new CompletableFuture<Void>(); Platform.runLater(() -> { try { work.run(); done.complete(null); } catch(Throwable e) {done.completeExceptionally(e);} });
        done.get(15,TimeUnit.SECONDS);
    }
    protected void until(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<end) {boolean[]ok={false};fx(() -> ok[0]=condition.getAsBoolean());if(ok[0])return;Thread.sleep(15);} fail("UI timed out");
    }
    protected javafx.scene.Node node(String id) {
        for(Window w:Window.getWindows()) if(w.isShowing() && w.getScene()!=null) {
            var n=w.getScene().lookup("#"+id); if(n!=null)return n;
        } return null;
    }
    protected void click(String id) { ((Button)node(id)).fire(); }
    @SuppressWarnings("unchecked") protected void form(String name,String cccd,RelationToHead relation) {
        ((TextField)node("resident_ho_ten")).setText(name);
        ((TextField)node("resident_ngay_sinh")).setText("1990-01-01");
        ((TextField)node("resident_ngay_chuyen_den")).setText("2024-01-01");
        ((TextField)node("resident_so_cccd")).setText(cccd);
        ((ComboBox<Gender>)node("residentGender")).setValue(Gender.NAM);
        ((ComboBox<RelationToHead>)node("residentRelation")).setValue(relation);
    }
    protected int rowCount() {return ((TableView<?>)node("residentTable")).getItems().size();}
    @Test void createRefreshesListAndDuplicateClickDoesNotInsertTwice() throws Exception {
        fx(() -> {click("addResidentButton");form("Nguyễn Văn An","028001000001",RelationToHead.CHU_HO);click("saveResidentButton");click("saveResidentButton");});
        until(() -> !busy && rowCount()==1);
        assertEquals(1,residents.countByMaHo(HOUSE)); fx(() -> assertTrue(((Label)node("residentCount")).getText().contains("1")));
    }
    @Test void adultWithoutCccdRetainsInputAndDisplaysFieldError() throws Exception {
        fx(() -> {click("addResidentButton");form("Nguyễn Văn An","",RelationToHead.CHU_HO);click("saveResidentButton");});
        until(() -> !((Label)node("residentError_so_cccd")).getText().isEmpty());
        fx(() -> {assertTrue(((Label)node("residentError_so_cccd")).getText().contains("14"));assertEquals("Nguyễn Văn An",((TextField)node("resident_ho_ten")).getText());});
        assertEquals(0,residents.countByMaHo(HOUSE));
    }
    @Test void duplicateCccdIsRejectedByExistingService() throws Exception {
        var service=new ResidentServiceImpl(residents,households,new RoleBasedAuthorizationService(null));
        assertTrue(service.createForHousehold(new ResidentForm(HOUSE,"Nguyễn Văn An","1990-01-01",Gender.NAM,"028001000001",RelationToHead.CHU_HO,"2024-01-01"),null).isSuccess());
        fx(() -> {click("addResidentButton");form("Trần Văn Bình","028001000001",RelationToHead.CON);click("saveResidentButton");});
        until(() -> !((Label)node("residentError_so_cccd")).getText().isEmpty()); assertEquals(1,residents.countByMaHo(HOUSE));
    }
    @Test void vacantApartmentRefusalComesFromBackend() throws Exception {
        households.update(HOUSE,28,new BigDecimal("80"),LocalDate.of(2024,1,1),ApartmentStatus.CAN_HO_TRONG);
        fx(() -> {click("addResidentButton");form("Nguyễn Văn An","028001000001",RelationToHead.CHU_HO);click("saveResidentButton");});
        until(() -> !((Label)node("residentError_ma_ho")).getText().isEmpty()); assertEquals(0,residents.countByMaHo(HOUSE));
    }
    @Test void headReplacementRequiresConfirmationAndDemotesOldHead() throws Exception {
        var service=new ResidentServiceImpl(residents,households,new RoleBasedAuthorizationService(null));
        service.createForHousehold(new ResidentForm(HOUSE,"Nguyễn Văn An","1990-01-01",Gender.NAM,"028001000001",RelationToHead.CHU_HO,"2024-01-01"),null);
        fx(() -> {click("addResidentButton");form("Trần Văn Bình","028001000002",RelationToHead.CHU_HO);click("saveResidentButton");});
        until(() -> Window.getWindows().stream().anyMatch(w -> w.isShowing() && w.getScene().getRoot() instanceof DialogPane p && p.getButtonTypes().contains(ButtonType.OK)));
        assertEquals(1,residents.countByMaHo(HOUSE));
        fx(() -> { for(Window w:List.copyOf(Window.getWindows())) if(w.isShowing() && w.getScene().getRoot() instanceof DialogPane p && p.getButtonTypes().contains(ButtonType.OK)) ((Button)p.lookupButton(ButtonType.OK)).fire(); });
        until(() -> !busy && rowCount()==2); assertEquals("Trần Văn Bình",residents.findHead(HOUSE).orElseThrow().hoTen());
    }
    @Test void accountantCannotCreateAndClosedPanelClearsData() throws Exception {
        fx(() -> {FeeCategorySessionFixture.use(Role.ACCOUNTANT); click("reloadResidentsButton");});
        until(() -> !busy);
        fx(() -> {assertTrue(((Button)node("addResidentButton")).isDisabled());panel.close();assertEquals(0,rowCount());assertFalse(busy);});
    }
}
