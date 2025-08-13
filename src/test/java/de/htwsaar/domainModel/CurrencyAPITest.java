package de.htwsaar.domainModel;

import de.htwsaar.exceptions.CurrencyApiException;
import okhttp3.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyAPITest {

    @Mock OkHttpClient mockClient;
    @Mock Call mockCall;
    @Mock Response mockResponse;
    @Mock ResponseBody mockBody;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private CurrencyAPI createCurrencyApiWithMockResponse(String jsonResponse) throws IOException {
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.string()).thenReturn(jsonResponse);
        return new CurrencyAPI("test-key", mockClient);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> usdRatesTestCases() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(
                LocalDate.of(2020,1,1),
                Map.of("USD", 1.0, "EUR", 0.89, "JPY", 108.6)
            ),
            org.junit.jupiter.params.provider.Arguments.of(
                LocalDate.of(2000,5,15),
                Map.of("USD", 1.0, "EUR", 1.1, "JPY", 106.5)
            ),
            org.junit.jupiter.params.provider.Arguments.of(
                LocalDate.of(1999,12,31),
                Map.of("USD", 1.0, "EUR", 1.2, "JPY", 102.0)
            )
        );
    }

    @ParameterizedTest(name = "API returns correct rates for {0}")
    @MethodSource("usdRatesTestCases")
    void testGetAllUsdRatesForDate(LocalDate date, Map<String, Double> expectedRates) throws Exception {
        String json = "{\"rates\":" + mapToJsonObject(expectedRates) + "}";
        CurrencyAPI api = createCurrencyApiWithMockResponse(json);
        assertEquals(expectedRates, api.getAllUsdRatesForDate(date));
    }

    private String mapToJsonObject(Map<String, Double> map) {
        return map.entrySet().stream()
            .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
            .collect(Collectors.joining(",", "{", "}"));
    }

    @Test
    @DisplayName("Returns empty map for empty, malformed, or missing rates JSON")
    void getAllUsdRatesForDate_EmptyMalformedOrMissingRates() throws Exception {
        // Empty JSON
        CurrencyAPI apiEmpty = createCurrencyApiWithMockResponse("{}");
        assertEquals(Collections.emptyMap(), apiEmpty.getAllUsdRatesForDate(LocalDate.now()));

        // Malformed JSON (not an object)
        CurrencyAPI apiMalformed = createCurrencyApiWithMockResponse("not json");
        assertEquals(Collections.emptyMap(), apiMalformed.getAllUsdRatesForDate(LocalDate.now()));

        // Null body
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(null);
        CurrencyAPI apiNull = new CurrencyAPI("test-key", mockClient);
        assertEquals(Collections.emptyMap(), apiNull.getAllUsdRatesForDate(LocalDate.now()));

        // Missing "rates" key
        CurrencyAPI apiNoRates = createCurrencyApiWithMockResponse("{\"foo\":123}");
        assertEquals(Collections.emptyMap(), apiNoRates.getAllUsdRatesForDate(LocalDate.now()));
    }

    @Test
    @DisplayName("Throws CurrencyApiException on network/API error")
    void getAllUsdRatesForDateException() throws Exception {
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Network error"));

        CurrencyAPI api = new CurrencyAPI("test-key", mockClient);

        CurrencyApiException ex = assertThrows(CurrencyApiException.class,
            () -> api.getAllUsdRatesForDate(LocalDate.of(2000,1,1)));
        assertTrue(ex.getMessage().contains("Failed to fetch rates for 2000-01-01"));
        assertTrue(ex.getCause() instanceof IOException);
        assertTrue(ex.getCause().getMessage().contains("Network error"));
    }

    @Test
    @DisplayName("Correctly fetches and caches currency names")
    void getAllCurrencyNamesAndCaching() throws Exception {
        // Prepare fake currency names JSON
        Map<String, String> names = Map.of("USD", "US Dollar", "EUR", "Euro", "JPY", "Japanese Yen");
        String json = mapToJsonObjectString(names);

        CurrencyAPI api = createCurrencyApiWithMockResponseForNames(json);

        // First call fetches and caches
        Map<String, String> result = api.getAllCurrencyNames();
        assertEquals(names, result);

        // Second call should return cached map (no new HTTP call)
        Map<String, String> cached = api.getAllCurrencyNames();
        assertSame(result, cached, "Should return the cached map instance");
    }

    private CurrencyAPI createCurrencyApiWithMockResponseForNames(String jsonResponse) throws IOException {
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);

        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.body()).thenReturn(body);
        when(body.string()).thenReturn(jsonResponse);

        return new CurrencyAPI("test-key", client);
    }

    private String mapToJsonObjectString(Map<String, String> map) {
        return map.entrySet().stream()
            .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }

    @Test
    @DisplayName("getFullNameForCode returns correct name or null")
    void getFullNameForCode() throws Exception {
        Map<String, String> names = Map.of("USD", "US Dollar", "EUR", "Euro");
        String json = mapToJsonObjectString(names);
        CurrencyAPI api = createCurrencyApiWithMockResponseForNames(json);

        assertEquals("US Dollar", api.getFullNameForCode("USD"));
        assertEquals("Euro", api.getFullNameForCode("EUR"));
        assertNull(api.getFullNameForCode("JPY"));
    }
}
