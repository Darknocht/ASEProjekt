package de.htwsaar.presentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {
    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        logger.info("Launching CurrencyConverterApp...");
        try {
            javafx.application.Application.launch(CurrencyConverterApp.class, args);
        } catch (Exception e) {
            logger.error("Exception during JavaFX application launch:");
            e.printStackTrace();
        }
        logger.info("If you see this message, JavaFX launch has exited.");
    }
}