package de.htwsaar.domainModel;

import de.htwsaar.presentation.CurrencyConverterView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.Node;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for managing currency conversion logic and chart updates in the GUI.
 * The controller is "smart": it fetches all data and passes it to the view.
 */
public class CurrencyConverterController {
    private final CurrencyConverterView view;
    private final DatabaseManager dbManager;
    private boolean isUpdating = false;
    private static final int CHART_MAX_POINTS = 300;
    private static final String DEFAULT_FROM_CURRENCY = "EUR";
    private static final String DEFAULT_TO_CURRENCY = "USD";
    private static final String NO_DATA_MSG = "No data available for the selected period and currencies.";

    public enum ChartPeriod {
        DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL
    }

    public CurrencyConverterController(CurrencyConverterView view, DatabaseManager dbManager) {
        this.view = view;
        this.dbManager = dbManager;
        initialize();
    }

    private void initialize() {
        try {
            Map<String, String> codeToName = dbManager.getCurrencyNames();
            view.setCurrencyNameMappings(codeToName);
            view.setCurrencyComboBoxItems(new ArrayList<>(codeToName.keySet()));

            selectDefaultCurrencies(codeToName);

            view.fromAmountField.setText("1");

            updateChartAsync(ChartPeriod.ALL);
            convertAmount(true);
            updateInfoBox();

            setupChartPeriodButtons();

            view.fromCurrency.setOnAction(e -> {
                updateChartAsync(ChartPeriod.ALL);
                convertAmount(true);
                updateInfoBox();
            });
            view.toCurrency.setOnAction(e -> {
                updateChartAsync(ChartPeriod.ALL);
                convertAmount(true);
                updateInfoBox();
            });

            view.fromAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!isUpdating) {
                    convertAmount(true);
                }
            });
            view.toAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!isUpdating) {
                    convertAmount(false);
                }
            });
        } catch (Exception e) {
            showErrorAlert("Initialization Error", "Failed to load currencies: " + e.getMessage());
        }
    }

    private void setupChartPeriodButtons() {
        Map<ChartPeriod, javafx.scene.control.Button> periodButtonMap = Map.of(
                ChartPeriod.DAY, view.lastDayButton,
                ChartPeriod.WEEK, view.lastWeekButton,
                ChartPeriod.MONTH, view.lastMonthButton,
                ChartPeriod.YEAR, view.lastYearButton,
                ChartPeriod.FIVE_YEARS, view.lastFiveYearsButton,
                ChartPeriod.ALL, view.allTimeButton
        );
        for (Map.Entry<ChartPeriod, javafx.scene.control.Button> entry : periodButtonMap.entrySet()) {
            entry.getValue().setOnAction(e -> updateChartAsync(entry.getKey()));
        }
    }

    private void selectDefaultCurrencies(Map<String, String> codeToName) {
        if (codeToName.isEmpty()) return;
        List<String> codes = new ArrayList<>(codeToName.keySet());
        String euroName = codeToName.get(DEFAULT_FROM_CURRENCY);
        String usdName = codeToName.get(DEFAULT_TO_CURRENCY);

        if (euroName != null) {
            view.fromCurrency.getSelectionModel().select(euroName);
        } else {
            view.fromCurrency.getSelectionModel().select(0);
        }
        if (usdName != null) {
            view.toCurrency.getSelectionModel().select(usdName);
        } else {
            view.toCurrency.getSelectionModel().select(codes.size() > 1 ? 1 : 0);
        }
    }

    private void convertAmount(boolean fromChanged) {
        String fromCode = view.getSelectedFromCode();
        String toCode = view.getSelectedToCode();

        if (fromCode == null || toCode == null) return;

        isUpdating = true;
        try {
            if (fromChanged) {
                String amountStr = view.fromAmountField.getText().trim();
                double amount = parseAmount(amountStr);
                double rate = dbManager.getLatestExchangeRate(fromCode, toCode);
                double converted = amount * rate;
                view.toAmountField.setText(String.format("%.2f", converted));
            } else {
                String amountStr = view.toAmountField.getText().trim();
                double amount = parseAmount(amountStr);
                double rate = dbManager.getLatestExchangeRate(toCode, fromCode);
                double converted = amount * rate;
                view.fromAmountField.setText(String.format("%.2f", converted));
            }
        } catch (Exception e) {
            showErrorAlert("Conversion Error", "Failed to convert amount: " + e.getMessage());
        } finally {
            isUpdating = false;
        }
    }

    private static double parseAmount(String amountStr) {
        try {
            return amountStr.isEmpty() ? 0 : Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Controller fetches and formats all info box data, and passes it to the view.
     */
    private void updateInfoBox() {
        String fromCode = view.getSelectedFromCode();
        String toCode = view.getSelectedToCode();
        Map<String, String> codeToName = view.getCodeToNameMap();

        String fromFullName = codeToName.get(fromCode);
        String toFullName = codeToName.get(toCode);

        double rate = 1.0;
        try {
            if (fromCode != null && toCode != null) {
                rate = dbManager.getLatestExchangeRate(fromCode, toCode);
            }
        } catch (Exception e) {
            rate = 1.0;
        }
        String rateStr = CurrencyConverterView.formatAmount(rate);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM"));

        view.updateInfoBox(rateStr, fromFullName, toFullName, dateStr);
    }

    private void showErrorAlert(String title, String message) {
        FXUtils.showAlert(Alert.AlertType.ERROR, title, message);
    }

    public void updateChartAsync(ChartPeriod period) {
        String fromCode = view.getSelectedFromCode();
        String toCode = view.getSelectedToCode();

        if (fromCode == null || toCode == null) return;

        setControlsDisabled(true);

        Task<XYChart.Series<String, Number>> chartTask = new Task<>() {
            private String errorMessage = null;

            @Override
            protected XYChart.Series<String, Number> call() {
                String[] overlap = getOverlappingDates(fromCode, toCode);
                if (overlap == null) return null;

                String startDate = adjustStartDate(overlap[0], overlap[1], period);
                String endDate = overlap[1];

                if (startDate.compareTo(endDate) > 0) {
                    errorMessage = "No overlapping data period found for the selected currencies.";
                    return null;
                }

                Map<String, Double> fromRates = fetchRates(fromCode, startDate, endDate);
                Map<String, Double> toRates = fetchRates(toCode, startDate, endDate);

                if (fromRates == null || toRates == null) return null;

                return buildSeries(fromCode, toCode, fromRates, toRates);
            }

            @Override
            protected void succeeded() {
                safeSetChartData(getValue(), errorMessage);
                setControlsDisabled(false);
            }

            @Override
            protected void failed() {
                showErrorAlert("Chart Error",
                        "Failed to update chart: " + (getException() != null ? getException().getMessage() : "Unknown error"));
                setControlsDisabled(false);
            }
        };

        FXUtils.showProgress(view.progressIndicator, true);
        new Thread(chartTask).start();
    }

    private String[] getOverlappingDates(String fromCode, String toCode) {
        try {
            String fromStart = dbManager.getFirstValidDate(fromCode);
            String fromEnd = dbManager.getLastValidDate(fromCode);
            String toStart = dbManager.getFirstValidDate(toCode);
            String toEnd = dbManager.getLastValidDate(toCode);

            if (fromStart == null || fromEnd == null || toStart == null || toEnd == null) {
                setError("No valid data found for one or both currencies.");
                return null;
            }
            String overlapStart = fromStart.compareTo(toStart) > 0 ? fromStart : toStart;
            String overlapEnd = fromEnd.compareTo(toEnd) < 0 ? fromEnd : toEnd;
            return new String[]{overlapStart, overlapEnd};
        } catch (Exception e) {
            setError("Failed to fetch date range: " + e.getMessage());
            return null;
        }
    }

    private static String adjustStartDate(String overlapStart, String overlapEnd, ChartPeriod period) {
        if (period == ChartPeriod.ALL) return overlapStart;
        try {
            LocalDate end = LocalDate.parse(overlapEnd);
            LocalDate start;
            switch (period) {
                case DAY:       start = end.minusDays(1); break;
                case WEEK:      start = end.minusWeeks(1); break;
                case MONTH:     start = end.minusMonths(1); break;
                case YEAR:      start = end.minusYears(1); break;
                case FIVE_YEARS:start = end.minusYears(5); break;
                default:        start = LocalDate.parse(overlapStart);
            }
            if (start.isBefore(LocalDate.parse(overlapStart))) start = LocalDate.parse(overlapStart);
            return start.toString();
        } catch (Exception ex) {
            return overlapStart;
        }
    }

    private Map<String, Double> fetchRates(String currency, String startDate, String endDate) {
        try {
            return dbManager.getDownsampledRates(currency, startDate, endDate, CHART_MAX_POINTS);
        } catch (Exception e) {
            setError("Failed to fetch rates: " + e.getMessage());
            return Map.of();
        }
    }

    private XYChart.Series<String, Number> buildSeries(String fromCode, String toCode,
                                                       Map<String, Double> fromRates, Map<String, Double> toRates) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(fromCode + " to " + toCode);

        for (String date : fromRates.keySet()) {
            Double fromRate = fromRates.get(date);
            Double toRate = toRates.get(date);
            if (isValidRate(fromRate) && isValidRate(toRate) && fromRate != 0.0) {
                series.getData().add(new XYChart.Data<>(date, toRate / fromRate));
            }
        }
        return (series.getData() != null && !series.getData().isEmpty()) ? series : null;
    }

    private static boolean isValidRate(Double rate) {
        return rate != null && rate != -1.0 && rate != 0.0;
    }

    private static void setError(String message) {
        System.err.println("Chart error: " + message);
    }

    private void safeSetChartData(XYChart.Series<String, Number> series, String errorMessage) {
        Platform.runLater(() -> {
            view.lineChart.getData().clear();

            if (hasValidSeriesData(series)) {
                addSeriesToChart(series);
                colorChartLine(series);
            } else {
                FXUtils.showAlert(Alert.AlertType.INFORMATION, "No Data",
                        errorMessage != null ? errorMessage : NO_DATA_MSG);
            }

            view.lineChart.applyCss();
            view.lineChart.layout();
        });
    }

    private static boolean hasValidSeriesData(XYChart.Series<String, Number> series) {
        return series != null && series.getData() != null && !series.getData().isEmpty();
    }

    private void addSeriesToChart(XYChart.Series<String, Number> series) {
        view.lineChart.getData().add(series);
        view.lineChart.setCreateSymbols(false);
    }

    private void colorChartLine(XYChart.Series<String, Number> series) {
        double first = series.getData().get(0).getYValue().doubleValue();
        double last = series.getData().get(series.getData().size() - 1).getYValue().doubleValue();
        String color = getLineColor(first, last);

        Platform.runLater(() -> {
            Node line = series.getNode() != null ? series.getNode().lookup(".chart-series-line") : null;
            if (line != null) {
                line.setStyle("-fx-stroke: " + color + ";");
            }
        });
    }

    private static String getLineColor(double first, double last) {
        if (last > first) return "green";
        if (last < first) return "red";
        return "grey";
    }

    private void setControlsDisabled(boolean disabled) {
        Platform.runLater(() -> {
            view.fromCurrency.setDisable(disabled);
            view.toCurrency.setDisable(disabled);
            view.fromAmountField.setDisable(disabled);
            view.toAmountField.setDisable(disabled);
            view.progressIndicator.setVisible(disabled);
        });
    }
}
