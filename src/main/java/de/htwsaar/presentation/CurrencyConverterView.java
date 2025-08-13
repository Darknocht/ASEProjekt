package de.htwsaar.presentation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * View component for the currency converter UI.
 * This class is "dumb": it only displays what it is given.
 */
public class CurrencyConverterView {

    public final ComboBox<String> fromCurrency = new ComboBox<>();
    public final ComboBox<String> toCurrency = new ComboBox<>();
    public final TextField fromAmountField = new TextField("1");
    public final TextField toAmountField = new TextField();
    public final ProgressIndicator progressIndicator = new ProgressIndicator();
    public final LineChart<String, Number> lineChart;

    public final Button lastDayButton = new Button("Last Day");
    public final Button lastWeekButton = new Button("Week");
    public final Button lastMonthButton = new Button("Month");
    public final Button lastYearButton = new Button("Year");
    public final Button lastFiveYearsButton = new Button("5 Years");
    public final Button allTimeButton = new Button("All Time");

    public final Label infoHeadline = new Label();
    public final Label infoConversion = new Label();
    public final Label infoDate = new Label();
    private final VBox infoBox = new VBox(2, infoHeadline, infoConversion, infoDate);

    private static final int PANEL_PADDING = 20;
    private static final int PANEL_SPACING = 10;
    private static final int INFOBOX_SPACING = 2;
    private static final int CHART_CONTROLS_SPACING = 10;

    private Map<String, String> codeToName = Collections.emptyMap();
    private Map<String, String> nameToCode = Collections.emptyMap();

    public CurrencyConverterView(LineChart<String, Number> chart) {
        this.lineChart = chart;
        progressIndicator.setVisible(false);

        infoBox.getStyleClass().add("infobox");
        infoHeadline.getStyleClass().add("infobox-line1");
        infoConversion.getStyleClass().add("infobox-line2");
        infoDate.getStyleClass().add("infobox-line3");
    }

    /**
     * Sets the mappings from currency code to full name.
     * @param codeToName map of code to full name
     */
    public void setCurrencyNameMappings(Map<String, String> codeToName) {
        this.codeToName = codeToName != null ? new HashMap<>(codeToName) : Collections.emptyMap();
        Map<String, String> rev = new HashMap<>();
        for (Map.Entry<String, String> entry : this.codeToName.entrySet()) {
            rev.put(entry.getValue(), entry.getKey());
        }
        this.nameToCode = rev;
    }

    /**
     * Sets the ComboBox items using a list of currency codes, sorted by currency name.
     * @param codes currency codes
     */
    public void setCurrencyComboBoxItems(List<String> codes) {
        if (codes == null) {
            fromCurrency.getItems().clear();
            toCurrency.getItems().clear();
            return;
        }
        List<String> fullNames = codes.stream()
                .map(code -> codeToName.getOrDefault(code, code))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        fromCurrency.getItems().setAll(fullNames);
        toCurrency.getItems().setAll(fullNames);
    }

    /**
     * @return selected from-currency code, or null if not found
     */
    public String getSelectedFromCode() {
        String fullName = fromCurrency.getValue();
        return fullName != null ? nameToCode.getOrDefault(fullName, fullName) : null;
    }

    /**
     * @return selected to-currency code, or null if not found
     */
    public String getSelectedToCode() {
        String fullName = toCurrency.getValue();
        return fullName != null ? nameToCode.getOrDefault(fullName, fullName) : null;
    }

    /**
     * Updates the info box with all necessary display data.
     * @param rateStr formatted exchange rate (e.g., "1.09")
     * @param fromFullName full name of the from currency
     * @param toFullName full name of the to currency
     * @param dateStr formatted date string (e.g., "6 Jul")
     */
    public void updateInfoBox(String rateStr, String fromFullName, String toFullName, String dateStr) {
        infoHeadline.setText(String.format("1 %s equals", fromFullName != null ? fromFullName : ""));
        infoConversion.setText(String.format("%s %s", rateStr != null ? rateStr : "1.00", toFullName != null ? toFullName : ""));
        infoDate.setText(dateStr != null ? dateStr : "");
    }

    /**
     * Formats an amount for display.
     * @param value amount to format
     * @return formatted string
     */
    public static String formatAmount(double value) {
        if (Math.abs(value) >= 1) {
            return String.format("%,.2f", value);
        } else {
            String formatted = String.format("%,.8f", value);
            formatted = formatted.indexOf('.') < 0 ? formatted : formatted.replaceAll("([0-9])0+$", "$1").replaceAll("\\.$", "");
            return formatted;
        }
    }

    /**
     * @return unmodifiable map of code to name
     */
    public Map<String, String> getCodeToNameMap() {
        return Collections.unmodifiableMap(codeToName);
    }

    /**
     * Creates the left panel (input and info).
     * @return VBox panel
     */
    public VBox createLeftPanel() {
        HBox fromBox = new HBox(5, fromCurrency, fromAmountField);
        fromBox.setAlignment(Pos.CENTER_LEFT);

        HBox toBox = new HBox(5, toCurrency, toAmountField);
        toBox.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(PANEL_SPACING,
                infoBox,
                new Label("From:"), fromBox,
                new Label("To:"), toBox,
                progressIndicator
        );
        panel.setPadding(new Insets(PANEL_PADDING));
        panel.setAlignment(Pos.TOP_LEFT);

        return panel;
    }

    /**
     * Creates the right panel (chart controls and chart).
     * @param chartControls HBox of chart control buttons
     * @return VBox panel
     */
    public VBox createRightPanel(HBox chartControls) {
        VBox panel = new VBox(PANEL_SPACING, chartControls, lineChart);
        panel.setPadding(new Insets(PANEL_PADDING));
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }

    /**
     * Creates the chart period control buttons.
     * @return HBox of buttons
     */
    public HBox createChartControls() {
        HBox chartControls = new HBox(CHART_CONTROLS_SPACING,
                lastDayButton, lastWeekButton, lastMonthButton,
                lastYearButton, lastFiveYearsButton, allTimeButton
        );
        chartControls.setAlignment(Pos.CENTER);
        chartControls.setPadding(new Insets(0, 0, 10, 0));
        return chartControls;
    }
}
