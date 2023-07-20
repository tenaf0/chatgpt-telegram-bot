package hu.garaba;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.SQLException;

public class Main {
    public static final System.Logger LOGGER = System.getLogger(Main.class.getCanonicalName());

    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            BotSession botSession = botsApi.registerBot(new Bot());

            /*Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.log(System.Logger.Level.DEBUG, "Started shutdown sequence");
                botSession.stop();
                LOGGER.log(System.Logger.Level.DEBUG, "Ended shutdown sequence");
            }));*/
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to start bot", e);
        }
    }
}