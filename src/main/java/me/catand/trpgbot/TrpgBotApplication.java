package me.catand.trpgbot;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TrpgBotApplication extends Application {

	public static void main(String[] args) {
		SpringApplication.run(TrpgBotApplication.class, args);
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		Platform.runLater(() -> {
			// Your JavaFX code here
		});
	}
}
