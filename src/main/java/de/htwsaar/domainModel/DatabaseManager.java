package de.htwsaar.domainModel;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all database operations for currency rates and names.
 */
public class DatabaseManager implements AutoCloseable {
    private String DB_URL = "jdbc:sqlite:data/Exchange_Rates.db";
    private static final String EXCHANGE_RATE_TABLE = "Exchange_Rate_Report";
    private static final String CURRENCY_NAMES_TABLE = "Currency_Names";
    private static final String ISO_DATE_COLUMN = "iso_date";
    private static final String DATE_COLUMN = "Date";
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);

    private Connection conn;
    private List<String> cachedCurrencyCodes = null;

    public DatabaseManager() {
        try {
            this.conn = DriverManager.getConnection(DB_URL);
            DatabaseMetaData meta = this.conn.getMetaData();
            LOGGER.info("Database connection established.");
        } catch (SQLException e) {
            LOGGER.error("Failed to establish database connection: {}", e.getMessage());
        }
    }

    public DatabaseManager(String DB_URL) {
        this.DB_URL = DB_URL;
        try {
            this.conn = DriverManager.getConnection(DB_URL);
            DatabaseMetaData meta = this.conn.getMetaData();
            LOGGER.info("Database connection established.");
        } catch (SQLException e) {
            LOGGER.error("Failed to establish database connection: {}", e.getMessage());
        }
    }

    public Connection getConnection() {
        return this.conn;
    }

    /**
     * @param currency Currency code
     * @throws SQLException If DB error
     * @throws IllegalArgumentException If invalid currency
     */
    private void validateCurrencyCode(String currency) throws SQLException {
        if (cachedCurrencyCodes == null) {
            cachedCurrencyCodes = fetchAllCurrencyCodes();
        }
        if (!cachedCurrencyCodes.contains(currency)) {
            throw new IllegalArgumentException("Invalid currency column: " + currency);
        }
    }

    /**
     * @return Sorted list of currency codes
     * @throws SQLException If DB error
     */
    private List<String> fetchAllCurrencyCodes() throws SQLException {
        List<String> currencies = new ArrayList<>();
        String sql = "PRAGMA table_info(" + EXCHANGE_RATE_TABLE + ")";
        try (Statement stmt = this.conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String columnName = rs.getString("name");
                if (columnName.equalsIgnoreCase(ISO_DATE_COLUMN) || columnName.equalsIgnoreCase(DATE_COLUMN)) {
                    continue;
                }
                currencies.add(columnName);
            }
        }
        return currencies.stream().sorted().collect(Collectors.toList());
    }

    /**
     * @return Sorted list of currency codes
     * @throws SQLException If DB error
     */
    public List<String> getAllCurrencyCodes() throws SQLException {
        cachedCurrencyCodes = fetchAllCurrencyCodes();
        return new ArrayList<>(cachedCurrencyCodes);
    }

    /**
     * @return Map of code to full name
     * @throws SQLException If DB error
     */
    public Map<String, String> getCurrencyNames() throws SQLException {
        Map<String, String> currencyNames = new LinkedHashMap<>();
        String sql = "SELECT code, full_name FROM " + CURRENCY_NAMES_TABLE;
        try (Statement stmt = this.conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String code = rs.getString("code");
                String fullName = rs.getString("full_name");
                currencyNames.put(code, fullName);
            }
        }
        return currencyNames;
    }

    /**
     * @param currency Currency code
     * @return First date with valid rate or null
     * @throws SQLException If DB error
     */
    public String getFirstValidDate(String currency) throws SQLException {
        validateCurrencyCode(currency);
        String sql = "SELECT " + ISO_DATE_COLUMN + " FROM " + EXCHANGE_RATE_TABLE +
                " WHERE \"" + currency + "\" IS NOT NULL AND \"" + currency + "\" != -1 " +
                "ORDER BY " + ISO_DATE_COLUMN + " ASC LIMIT 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(ISO_DATE_COLUMN);
            }
        }
        return null;
    }

    /**
     * @param currency Currency code
     * @return Last date with valid rate or null
     * @throws SQLException If DB error
     */
    public String getLastValidDate(String currency) throws SQLException {
        validateCurrencyCode(currency);
        String sql = "SELECT " + ISO_DATE_COLUMN + " FROM " + EXCHANGE_RATE_TABLE +
                " WHERE \"" + currency + "\" IS NOT NULL AND \"" + currency + "\" != -1 " +
                "ORDER BY " + ISO_DATE_COLUMN + " DESC LIMIT 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(ISO_DATE_COLUMN);
            }
        }
        return null;
    }

    /**
     * @return Latest date in DB
     * @throws SQLException If DB error or empty
     */
    public String getLatestDate() throws SQLException {
        String sql = "SELECT " + ISO_DATE_COLUMN + " FROM " + EXCHANGE_RATE_TABLE +
                " ORDER BY " + ISO_DATE_COLUMN + " DESC LIMIT 1";
        try (Statement stmt = this.conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(ISO_DATE_COLUMN);
            } else {
                throw new SQLException("No data found in the database.");
            }
        }
    }

    /**
     * @param from Source currency
     * @param to Target currency
     * @return Latest exchange rate (to/from)
     * @throws SQLException If DB error or missing data
     * @throws IllegalArgumentException If invalid currency
     */
    public double getLatestExchangeRate(String from, String to) throws SQLException {
        validateCurrencyCode(from);
        validateCurrencyCode(to);

        if (from.equals(to)) {
            return 1.0;
        }

        String sql = "SELECT \"" + from + "\", \"" + to + "\" FROM " + EXCHANGE_RATE_TABLE +
                " ORDER BY " + ISO_DATE_COLUMN + " DESC LIMIT 1";
        try (Statement stmt = this.conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                double fromRate = rs.getDouble(from);
                double toRate = rs.getDouble(to);
                if (fromRate == 0) {
                    throw new SQLException("From rate is zero.");
                }
                return toRate / fromRate;
            } else {
                throw new SQLException("No data found for the specified currencies.");
            }
        }
    }

    /**
     * @param currency Currency code
     * @param startDate Inclusive start date (ISO)
     * @param endDate Inclusive end date (ISO)
     * @param maxPoints Max points in result
     * @return Map of date to rate
     * @throws SQLException If DB error
     * @throws IllegalArgumentException If invalid currency
     */
    public Map<String, Double> getDownsampledRates(String currency, String startDate, String endDate, int maxPoints) throws SQLException {
        validateCurrencyCode(currency);

        String countSql = "SELECT COUNT(*) FROM " + EXCHANGE_RATE_TABLE +
                " WHERE " + ISO_DATE_COLUMN + " >= ? AND " + ISO_DATE_COLUMN + " <= ?";
        int totalRows;
        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            countStmt.setString(1, startDate);
            countStmt.setString(2, endDate);
            try (ResultSet rs = countStmt.executeQuery()) {
                totalRows = rs.next() ? rs.getInt(1) : 0;
            }
        }
        if (totalRows == 0) {
            return Collections.emptyMap();
        }

        int step = (int) Math.ceil((double) totalRows / maxPoints);
        if (step < 1) step = 1;

        String sql = "SELECT " + ISO_DATE_COLUMN + ", \"" + currency + "\" FROM (" +
                "  SELECT " + ISO_DATE_COLUMN + ", \"" + currency + "\", " +
                "         ROW_NUMBER() OVER (ORDER BY " + ISO_DATE_COLUMN + ") AS rn " +
                "  FROM " + EXCHANGE_RATE_TABLE +
                "  WHERE " + ISO_DATE_COLUMN + " >= ? AND " + ISO_DATE_COLUMN + " <= ?" +
                ") WHERE (rn - 1) % ? = 0 ORDER BY " + ISO_DATE_COLUMN + " ASC";

        Map<String, Double> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setInt(3, step);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString(ISO_DATE_COLUMN);
                    double value = rs.getDouble(currency);
                    if (rs.wasNull() || value == 0.0) {
                        continue;
                    }
                    result.put(date, value);
                }
            }
        }
        return result;
    }

    /**
     * @param currency Currency code
     * @throws SQLException If DB error
     */
    public void addCurrency(String currency) throws SQLException {
        getAllCurrencyCodes(); // Refresh cache
        if (cachedCurrencyCodes.contains(currency)) {
            LOGGER.info("Currency {} already exists.", currency);
            return;
        }
        String sql = "ALTER TABLE " + EXCHANGE_RATE_TABLE + " ADD COLUMN \"" + currency + "\" REAL";
        try (Statement stmt = this.conn.createStatement()) {
            stmt.executeUpdate(sql);
            LOGGER.info("Added new currency column: {}", currency);
            cachedCurrencyCodes = null; // Invalidate cache
        } catch (SQLException e) {
            if (e.getMessage().toLowerCase().contains("duplicate column name")) {
                LOGGER.info("Currency {} already exists.", currency);
            } else {
                throw e;
            }
        }
    }

    /**
     * @param code Currency code
     * @param fullName Full name
     * @throws SQLException If DB error
     */
    public void addCurrencyName(String code, String fullName) throws SQLException {
        String sql = "INSERT OR IGNORE INTO " + CURRENCY_NAMES_TABLE + " (code, full_name) VALUES (?, ?)";
        try (PreparedStatement pstmt = this.conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, fullName);
            pstmt.executeUpdate();
            LOGGER.info("Added currency name: {} ({})", code, fullName);
        }
    }

    /**
     * @param currency Currency code
     * @param dateIso ISO date
     * @param rate Exchange rate
     * @throws SQLException If DB error
     * @throws IllegalArgumentException If invalid currency
     */
    public void upsertRate(String currency, String dateIso, double rate) throws SQLException {
        validateCurrencyCode(currency);

        LocalDate localDate = LocalDate.parse(dateIso);
        String dateText = localDate.format(DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.ENGLISH));

        String insertSql = "INSERT OR IGNORE INTO " + EXCHANGE_RATE_TABLE +
                " (" + ISO_DATE_COLUMN + ", " + DATE_COLUMN + ") VALUES (?, ?)";
        try (PreparedStatement pstmt = this.conn.prepareStatement(insertSql)) {
            pstmt.setString(1, dateIso);
            pstmt.setString(2, dateText);
            pstmt.executeUpdate();
        }

        String updateSql = "UPDATE " + EXCHANGE_RATE_TABLE +
                " SET \"" + currency + "\" = ? WHERE " + ISO_DATE_COLUMN + " = ?";
        try (PreparedStatement pstmt = this.conn.prepareStatement(updateSql)) {
            pstmt.setDouble(1, rate);
            pstmt.setString(2, dateIso);
            pstmt.executeUpdate();
            LOGGER.info("Upserted rate for {} on {}: {}", currency, dateIso, rate);
        }
    }

    /**
     * Closes the database connection.
     */
    @Override
    public void close() {
        if (this.conn != null) {
            try {
                this.conn.close();
                LOGGER.info("Database connection closed.");
            } catch (SQLException e) {
                LOGGER.error("Error closing connection: {}", e.getMessage());
            } finally {
                this.conn = null;
            }
        }
    }
}
