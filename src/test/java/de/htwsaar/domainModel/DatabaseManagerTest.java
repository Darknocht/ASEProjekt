package de.htwsaar.domainModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    static DatabaseManager createInMemoryDbWithSchemaAndData() throws SQLException {
        DatabaseManager db = new DatabaseManager("jdbc:sqlite::memory:");
        Connection conn = db.getConnection();

        try (Statement stmt = conn.createStatement()) {
            // Drop table if exists
            stmt.execute("DROP TABLE IF EXISTS Exchange_Rate_Report");

            // Create the Exchange_Rate_Report table in 'wide' format
            stmt.execute(
                "CREATE TABLE Exchange_Rate_Report (" +
                    "iso_date TEXT PRIMARY KEY, " +
                    "Date TEXT, " +
                    "USD REAL, " +
                    "EUR REAL, " +
                    "PLN REAL, " +
                    "RUB REAL, " +
                    "JPY REAL" +
                ")"
            );

            // Insert data (one row per date, all rates as columns)
            stmt.execute(
                "INSERT INTO Exchange_Rate_Report (iso_date, Date, USD, EUR, PLN, RUB, JPY) VALUES " +
                        "('1994-01-03', 'Jan-03-1994', 1.0, NULL, NULL, NULL, NULL)," +
                        "('1994-01-04', 'Jan-04-1994', NULL, NULL, NULL, NULL, 100.0)," +
                        "('1994-01-31', 'Jan-31-1994', NULL, NULL, 4.0, NULL, NULL)," +
                        "('1998-10-30', 'Oct-30-1998', NULL, 1.0, NULL, NULL, NULL)," +
                        "('2000-12-12', 'Dec-12-2000', NULL, 1.13869278068777, NULL, NULL, NULL)," +
                        "('2001-01-26', 'Jan-26-2001', NULL, 1.08365843086259, NULL, NULL, NULL)," +
                        "('2003-05-05', 'May-05-2003', NULL, NULL, NULL, 30.0, NULL)," +
                        "('2012-07-02', 'Jul-02-2012', NULL, NULL, 3.3456, NULL, NULL)," +
                        "('2012-07-25', 'Jul-25-2012', NULL, NULL, 3.4719, NULL, NULL)," +
                        "('2025-01-01', 'Jan-01-2025', 1.0, NULL, NULL, NULL, NULL)," +
                        "('2025-06-01', 'Jun-01-2025', 1.0, 1.0, 4.0, 30.0, 100.0)"
            );
        }
        return db;
    }

    @Test
    @DisplayName("Initialisation of the class DatabaseManager")
    void databaseManagerInitialize() {
        assertDoesNotThrow(DatabaseManagerTest::createInMemoryDbWithSchemaAndData);
    }

    @Test
    @DisplayName("Verification if all currencies are correct")
    void getAllCurrencyNamesTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        String[] expected = {"EUR", "JPY", "PLN", "RUB", "USD"}; // Sorted order, as per fetchAllCurrencyCodes()
        List<String> actual = databaseManager.getAllCurrencyCodes();
        assertArrayEquals(expected, actual.toArray(), "Currency codes should match expected set");
    }

    @Test
    @DisplayName("Verification of the first date for each currency")
    void getFirstValidDateTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        assertEquals("1998-10-30", databaseManager.getFirstValidDate("EUR"));
        assertEquals("1994-01-03", databaseManager.getFirstValidDate("USD"));
        assertEquals("1994-01-31", databaseManager.getFirstValidDate("PLN"));
        assertEquals("2003-05-05", databaseManager.getFirstValidDate("RUB"));
        assertEquals("1994-01-04", databaseManager.getFirstValidDate("JPY"));
    }

    @Test
    @DisplayName("Verification of the last date for each currency")
    void getLastValidDateTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        String expected = "2025-06-01";
        for (String currency : List.of("EUR", "USD", "PLN", "RUB", "JPY")) {
            assertEquals(expected, databaseManager.getLastValidDate(currency),
                    "Last valid date for " + currency + " should be " + expected);
        }
    }

    @Test
    @DisplayName("Verification of the latest date without Exception")
    void getLatestDateTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        String latestDate = assertDoesNotThrow(databaseManager::getLatestDate);
        assertEquals("2025-06-01", latestDate, "The latest date should match the test data.");
    }

    @Test
    @DisplayName("Verification if the same currency returns 1.0")
    void getLatestExchangeRateWithSameCurrency() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        for (String code : List.of("EUR", "USD", "PLN", "RUB", "JPY")) {
            assertEquals(1.0, databaseManager.getLatestExchangeRate(code, code), 1e-9, code + " == " + code);
        }
    }

    @Test
    @DisplayName("Verification if the latest changes are correct")
    void getLatestExchangeRateWithDifferentCurrency() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        assertEquals(1, Math.round(databaseManager.getLatestExchangeRate("USD", "EUR")), "Round(USD --> EUR) = 1");
        assertEquals(4, Math.round(databaseManager.getLatestExchangeRate("USD", "PLN")), "Round(USD --> PLN) = 4");
    }

    @Test
    @DisplayName("Verification if Exception with unknown currency")
    void getLatestExchangeRateUnknownCurrency() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        assertThrows(IllegalArgumentException.class, () -> databaseManager.getLatestExchangeRate("USD", "FALSE"));
        assertThrows(IllegalArgumentException.class, () -> databaseManager.getLatestExchangeRate("FALSE", "USD"));
        assertThrows(IllegalArgumentException.class, () -> databaseManager.getLatestExchangeRate("FALSE", "FALSE"));
    }

    @Test
    @DisplayName("Verification if the changes are correct with the dates")
    void getDownsampledRatesTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();

        Map<String, Double> expectedMapUSD = Map.of(
                "2025-01-01", 1.0,
                "2025-06-01", 1.0
        );
        Map<String, Double> expectedMapEUR = Map.of(
                "2000-12-12", 1.13869278068777,
                "2001-01-26", 1.08365843086259
        );
        Map<String, Double> expectedMapPLN = Map.of(
                "2012-07-02", 3.3456,
                "2012-07-25", 3.4719
        );

        assertEquals(expectedMapUSD, databaseManager.getDownsampledRates("USD", "2025-01-01", "2025-06-01", 300));
        assertEquals(expectedMapEUR, databaseManager.getDownsampledRates("EUR", "2000-12-12", "2001-01-30", 10));
        assertEquals(expectedMapPLN, databaseManager.getDownsampledRates("PLN", "2012-07-01", "2012-07-30", 5));
    }

    @Test
    @DisplayName("Verification with unknown currency")
    void getDownsampledRatesUnknownCurrency() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        assertThrows(IllegalArgumentException.class,
                () -> databaseManager.getDownsampledRates("FALSE", "2025-01-01", "2025-06-01", 10));
    }

    @Test
    @DisplayName("Verification with already currencies")
    void addCurrencyTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        for (String code : List.of("USD", "EUR", "PLN", "RUB", "JPY")) {
            assertDoesNotThrow(() -> databaseManager.addCurrency(code), code + " should be already in the database");
        }
    }

    @Test
    @DisplayName("Closing without Exception")
    void closeTest() throws SQLException {
        DatabaseManager databaseManager = createInMemoryDbWithSchemaAndData();
        assertDoesNotThrow(databaseManager::close);
    }
}
