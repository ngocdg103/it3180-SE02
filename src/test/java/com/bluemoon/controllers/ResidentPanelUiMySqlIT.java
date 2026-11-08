package com.bluemoon.controllers;

import com.bluemoon.config.*;
import com.bluemoon.dao.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same UI acceptance cases using real Service/DAO/MySQL; owns only UI_US002_2801/P2801 rows. */
@EnabledIfEnvironmentVariable(named="BLUEMOON_MYSQL_TEST",matches="1")
class ResidentPanelUiMySqlIT extends ResidentPanelUiTest {
    private DatabaseConnection connections;
    @Override protected void repositories() throws Exception {
        connections=new DatabaseConnection(DatabaseConfig.load());
        sql("SELECT 1");
        for(String name:new String[]{"us001_household_apartment.sql","us002_residents.sql"}) {
            String script=Files.readString(Path.of("database/migrations",name)).replaceAll("(?m)^\\s*--.*$","");
            for(String command:script.split(";")) if(!command.isBlank())sql(command);
        }
        cleanup(); households=new HouseholdDAO(connections);residents=new ResidentDAO(connections);
    }
    private void sql(String sql) throws Exception {
        try(var c=connections.getConnection()) {
            assertNotNull(c.getCatalog()); assertTrue(c.getCatalog().endsWith("_test"));assertEquals("MySQL",c.getMetaData().getDatabaseProductName());
            try(var s=c.prepareStatement(sql)) {s.execute();}
        }
    }
    @Override protected void cleanup() throws Exception {
        if(connections==null)return;
        sql("DELETE FROM residents WHERE ma_ho='UI_US002_2801'");
        sql("DELETE FROM households WHERE ma_ho='UI_US002_2801'");
        sql("DELETE FROM apartments WHERE so_can_ho='P2801'");
    }
}
