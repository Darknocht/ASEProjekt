package de.htwsaar.domainModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import javafx.concurrent.Task;

class CurrencyUpdaterTest {
    @Test
    @DisplayName("Verification if does not throw")
    void syncDatabaseWithProgressTest(){
        CurrencyAPI api = new CurrencyAPI();
        DatabaseManager db = new DatabaseManager();
        CurrencyUpdater updater = new CurrencyUpdater(api, db);

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

        assertDoesNotThrow(syncTask::run);
    }
}