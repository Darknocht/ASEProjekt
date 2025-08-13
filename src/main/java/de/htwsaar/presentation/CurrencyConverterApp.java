package de.htwsaar.presentation;

import de.htwsaar.domainModel.CurrencyAPI;
import de.htwsaar.domainModel.CurrencyConverterController;
import de.htwsaar.domainModel.CurrencyUpdater;
import de.htwsaar.domainModel.DatabaseManager;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CurrencyConverterApp extends Application {
    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 600;
    private static final int LOADING_BOX_WIDTH = 400;
    private static final int LOADING_BOX_HEIGHT = 220;

    @Override
    public void start(Stage primaryStage) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            showStartupError("SQLite JDBC driver not found.");
            return;
        }

        CurrencyAPI api;
        DatabaseManager db;
        try {
            api = new CurrencyAPI();
            db = new DatabaseManager();
        } catch (Exception e) {
            showStartupError("Failed to initialize API or database: " + e.getMessage());
            return;
        }
        CurrencyUpdater updater = new CurrencyUpdater(api, db);

        // UI setup
        LineChart<String, Number> chart = createChart();
        CurrencyConverterView view = new CurrencyConverterView(chart);

        // Currency mappings
        Map<String, String> codeToName = createDefaultCurrencyMappings();
        view.setCurrencyNameMappings(codeToName);
        view.setCurrencyComboBoxItems(List.copyOf(codeToName.keySet()));

        // Set default selections for startup
        selectDefaultCurrencies(view, codeToName);

        // Controller handles all info box updates from here on
        CurrencyConverterController controller = new CurrencyConverterController(view, db);

        BorderPane root = new BorderPane();
        root.setLeft(view.createLeftPanel());
        HBox chartControls = view.createChartControls();
        root.setCenter(view.createRightPanel(chartControls));

        // Progress overlay
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.getStyleClass().add("loading-progress-bar");

        Label progressLabel = new Label("Loading currency data...");
        progressLabel.getStyleClass().add("loading-label");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("loading-error-label");

        VBox loadingBox = new VBox(20, progressLabel, progressBar, errorLabel);
        loadingBox.getStyleClass().add("loading-box");
        loadingBox.setMaxSize(LOADING_BOX_WIDTH, LOADING_BOX_HEIGHT);

        StackPane stack = new StackPane(root, loadingBox);
        loadingBox.setVisible(true);
        root.setDisable(true);

        Scene scene = new Scene(stack, WINDOW_WIDTH, WINDOW_HEIGHT);
        URL css = getClass().getResource("/dark-theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // Background sync task with progress
        Task<Void> syncTask = new Task<>() {
            @Override
            protected Void call() {
                updater.syncDatabaseWithProgress(
                        (processed, total) -> updateProgress(processed, total == 0 ? 1 : total),
                        this::updateMessage
                );
                return null;
            }
        };

        progressBar.progressProperty().bind(syncTask.progressProperty());
        progressLabel.textProperty().bind(syncTask.messageProperty());

        syncTask.setOnSucceeded(e -> {
            loadingBox.setVisible(false);
            root.setDisable(false);
            // Controller will update the info box as needed
        });

        syncTask.setOnFailed(e -> {
            loadingBox.setVisible(false);
            root.setDisable(false);
            errorLabel.setText("Failed to load currency data.");
        });

        new Thread(syncTask).start();

        primaryStage.setScene(scene);
        primaryStage.setTitle("Currency Converter");
        primaryStage.show();
    }

    /**
     * Creates and configures the main chart.
     */
    private static LineChart<String, Number> createChart() {
        LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        return chart;
    }

    /**
     * @return Default currency code-to-name mappings.
     */
    private static Map<String, String> createDefaultCurrencyMappings() {
        Map<String, String> codeToName = new LinkedHashMap<>();
        codeToName.put("EUR", "Euro");
        codeToName.put("USD", "US Dollar");
        codeToName.put("JPY", "Japanese Yen");
        codeToName.put("GBP", "British Pound");
        // ...add more as needed
        return Map.copyOf(codeToName);
    }

    /**
     * Selects default currencies in the ComboBoxes.
     */
    private static void selectDefaultCurrencies(CurrencyConverterView view, Map<String, String> codeToName) {
        String euroName = codeToName.get("EUR");
        String usdName = codeToName.get("USD");
        if (euroName != null) {
            view.fromCurrency.getSelectionModel().select(euroName);
        } else if (!codeToName.isEmpty()) {
            view.fromCurrency.getSelectionModel().select(0);
        }
        if (usdName != null) {
            view.toCurrency.getSelectionModel().select(usdName);
        } else if (codeToName.size() > 1) {
            view.toCurrency.getSelectionModel().select(1);
        } else if (!codeToName.isEmpty()) {
            view.toCurrency.getSelectionModel().select(0);
        }
    }

    /**
     * Shows a fatal error and exits.
     */
    private void showStartupError(String message) {
        System.err.println(message);
        javafx.application.Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
