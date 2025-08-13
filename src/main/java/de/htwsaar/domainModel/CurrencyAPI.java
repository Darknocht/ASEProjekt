package de.htwsaar.domainModel;

import de.htwsaar.exceptions.CurrencyApiException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches exchange rates and currency names from Open Exchange Rates API.
 * <p>
 * For personal use: API key is set in code for convenience.
 */
public class CurrencyAPI {
    // Set your API key here for personal use
    private static final String DEFAULT_API_KEY = "API_KEY";
    private final String apiKey;
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyAPI.class);
    private final OkHttpClient client;
    private volatile Map<String, String> codeToNameCache = null;

    /**
     * Uses the default API key.
     */
    public CurrencyAPI() {
        this.apiKey = DEFAULT_API_KEY;
        this.client = new OkHttpClient();
    }

    /**
     * @param apiKey API key (for testing or override)
     * @param client OkHttpClient instance
     */
    public CurrencyAPI(String apiKey, OkHttpClient client) {
        this.apiKey = apiKey;
        this.client = client;
    }

    /**
     * @param date Date to fetch rates for
     * @return Map of currency code to rate
     * @throws CurrencyApiException On API or network error
     */
    public Map<String, Double> getAllUsdRatesForDate(LocalDate date) throws CurrencyApiException {
        String url = String.format(
                "https://openexchangerates.org/api/historical/%d-%02d-%02d.json?app_id=%s",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(), apiKey
        );
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) {
                LOGGER.error("Empty response body for date {}", date);
                return Collections.emptyMap();
            }
            String jsonData = response.body().string();
            if (!jsonData.trim().startsWith("{")) {
                LOGGER.error("API did not return JSON for {}: {}", date, jsonData);
                return Collections.emptyMap();
            }
            JSONObject json = new JSONObject(jsonData);
            if (!json.has("rates")) {
                LOGGER.error("API error for {}: {}", date, json.optString("message", "unknown error"));
                return Collections.emptyMap();
            }
            JSONObject rates = json.getJSONObject("rates");
            Map<String, Double> result = new HashMap<>();
            for (String currency : rates.keySet()) {
                result.put(currency, rates.getDouble(currency));
            }
            return result;
        } catch (IOException e) {
            throw new CurrencyApiException("Failed to fetch rates for " + date, e);
        }
    }

    /**
     * @return Map of code to full name
     */
    public Map<String, String> getAllCurrencyNames() {
        Map<String, String> cache = codeToNameCache;
        if (cache != null) {
            return cache;
        }
        String url = "https://openexchangerates.org/api/currencies.json";
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() == null) {
                LOGGER.error("Empty response body for currency names");
                return Collections.emptyMap();
            }
            String jsonData = response.body().string();
            JSONObject json = new JSONObject(jsonData);
            Map<String, String> result = new HashMap<>();
            for (String code : json.keySet()) {
                result.put(code, json.getString(code));
            }
            Map<String, String> immutableResult = Collections.unmodifiableMap(result);
            codeToNameCache = immutableResult;
            return immutableResult;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch currency names: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * @param code Currency code
     * @return Full name or null if not found
     */
    public String getFullNameForCode(String code) {
        return getAllCurrencyNames().get(code);
    }
}
