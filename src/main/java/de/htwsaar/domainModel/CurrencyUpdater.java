package de.htwsaar.domainModel;

import de.htwsaar.exceptions.CurrencyApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Updates the local database with new currency rates and metadata from the API.
 */
public class CurrencyUpdater {

    private final CurrencyAPI api;
    private final DatabaseManager db;
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyUpdater.class);

    public CurrencyUpdater(CurrencyAPI api, DatabaseManager db) {
        this.api = api;
        this.db = db;
    }

    /**
     * Syncs the database with all missing days up to today.
     * @param progressCallback (processed, total)
     * @param messageCallback  status message
     */
    public void syncDatabaseWithProgress(BiConsumer<Integer, Integer> progressCallback, Consumer<String> messageCallback) {
        String lastDateStr = getLastDateSafe();
        if (lastDateStr == null) return;

        LocalDate lastDate = LocalDate.parse(lastDateStr);
        LocalDate today = LocalDate.now();
        LocalDate firstMissing = lastDate.plusDays(1);
        int totalDays = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(firstMissing, today.plusDays(1)));

        Set<String> currencyCodes = new HashSet<>(getCurrencyCodesSafe());
        Map<String, String> currencyNames = new HashMap<>(getCurrencyNamesSafe());

        int processed = 0;
        for (LocalDate date = firstMissing; !date.isAfter(today); date = date.plusDays(1)) {
            updateMessage(messageCallback, "Updating: " + date);
            processRatesForDate(date, currencyCodes, currencyNames);
            processed++;
            updateProgress(progressCallback, processed, totalDays);
        }
    }

    /**
     * Gets the most recent date in the database, or null on error.
     * @return ISO date string or null
     */
    private String getLastDateSafe() {
        try {
            return db.getLatestDate();
        } catch (Exception e) {
            LOGGER.error("Error fetching last date from DB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sends a status message to the callback, if provided.
     * @param messageCallback callback for status messages
     * @param message message to send
     */
    private void updateMessage(Consumer<String> messageCallback, String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * Sends progress info to the callback, if provided.
     * @param progressCallback callback for progress
     * @param processed number of items processed
     * @param total total items to process
     */
    private void updateProgress(BiConsumer<Integer, Integer> progressCallback, int processed, int total) {
        if (progressCallback != null) {
            progressCallback.accept(processed, total);
        }
    }

    /**
     * Updates the database with all rates for a single date.
     * @param date date to process
     * @param currencyCodes set of known currency codes (updated in-place)
     * @param currencyNames map of known currency names (updated in-place)
     */
    private void processRatesForDate(LocalDate date, Set<String> currencyCodes, Map<String, String> currencyNames) {
        Map<String, Double> rates = fetchRatesForDate(date);
        if (rates == null) return;

        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String currency = entry.getKey();
            double rate = entry.getValue();

            // Add currency column if missing
            if (!currencyCodes.contains(currency)) {
                try {
                    db.addCurrency(currency);
                    currencyCodes.add(currency);
                } catch (Exception e) {
                    LOGGER.error("Failed to add currency column {}: {}", currency, e.getMessage());
                }
            }

            // Add currency name if missing
            if (!currencyNames.containsKey(currency)) {
                String name = fetchCurrencyName(currency);
                try {
                    db.addCurrencyName(currency, name);
                    currencyNames.put(currency, name);
                } catch (SQLException e) {
                    LOGGER.error("Failed to add currency name {}: {}", currency, e.getMessage());
                }
            }

            upsertRateSafe(currency, date, rate);
        }
    }

    /**
     * Gets the full name for a currency code from the API, or falls back to the code.
     * @param currency currency code
     * @return full name or code
     */
    private String fetchCurrencyName(String currency) {
        try {
            String name = api.getFullNameForCode(currency);
            if (name == null || name.isBlank()) {
                return currency;
            }
            return name;
        } catch (Exception e) {
            LOGGER.warn("API call failed for currency name {}: {}", currency, e.getMessage());
            return currency;
        }
    }

    /**
     * Fetches rates for a given date from the API.
     * @param date date to fetch
     * @return map of currency to rate, or null on error
     */
    private Map<String, Double> fetchRatesForDate(LocalDate date) {
        try {
            return api.getAllUsdRatesForDate(date);
        } catch (CurrencyApiException e) {
            LOGGER.error("Error fetching rates from API: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets all currency codes from the database, or empty list on error.
     * @return list of currency codes
     */
    private List<String> getCurrencyCodesSafe() {
        try {
            return db.getAllCurrencyCodes();
        } catch (Exception e) {
            LOGGER.error("Failed to fetch currency codes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Gets all currency names from the database, or empty map on error.
     * @return map of code to name
     */
    private Map<String, String> getCurrencyNamesSafe() {
        try {
            return db.getCurrencyNames();
        } catch (Exception e) {
            LOGGER.error("Failed to fetch currency names: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Inserts or updates a rate in the database, logging any error.
     * @param currency currency code
     * @param date date
     * @param rate exchange rate
     */
    private void upsertRateSafe(String currency, LocalDate date, double rate) {
        try {
            db.upsertRate(currency, date.toString(), rate);
        } catch (Exception e) {
            LOGGER.error("Failed to upsert rate for {} on {}: {}", currency, date, e.getMessage());
        }
    }
}
