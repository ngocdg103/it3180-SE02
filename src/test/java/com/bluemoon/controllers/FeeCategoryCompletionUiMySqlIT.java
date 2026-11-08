package com.bluemoon.controllers;

import com.bluemoon.config.*;
import com.bluemoon.dao.*;
import com.bluemoon.models.FeeRate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
class FeeCategoryCompletionUiMySqlIT extends FeeCategoryCompletionUiTest {
    private DatabaseConnection connections;
    @Override protected void repository() throws Exception {
        connections=new DatabaseConnection(DatabaseConfig.load());
        for(String file:new String[]{"us005_fee_types.sql","us006_fee_rates.sql"}) {
            String script=Files.readString(Path.of("database/migrations",file)).replaceAll("(?m)^\\s*--.*$","");
            for(String command:script.split(";"))if(!command.isBlank())sql(command);
        }cleanup();repository=new FeeTypeDAO(connections);
    }
    private void sql(String command) throws Exception {
        try(var c=connections.getConnection()) {assertNotNull(c.getCatalog());assertTrue(c.getCatalog().endsWith("_test"));assertEquals("MySQL",c.getMetaData().getDatabaseProductName());try(var s=c.prepareStatement(command)){s.execute();}}
    }
    @Override protected void cleanup() throws Exception {
        if(connections==null)return;
        sql("DELETE FROM fee_rates WHERE ma_loai_thu='UI_FEE_1'");sql("DELETE FROM fee_types WHERE ma_loai_thu='UI_FEE_1'");
    }
    @Test void deactivationPreservesRateHistory() throws Exception {
        var rates=new FeeRateDAO(connections);var original=rates.insert(new FeeRate(null,CODE,new BigDecimal("7000"),LocalDate.of(2026,1,1),null));
        fx(() -> {select();click("deactivateFeeButton");});answer(javafx.scene.control.ButtonType.OK);until(() -> !module.busyProperty().get());
        assertEquals(java.util.List.of(original),rates.findHistory(CODE));
    }
}
