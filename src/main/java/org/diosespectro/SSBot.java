package org.diosespectro;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SSBot extends TelegramLongPollingBot {
    private boolean screaming = false;
    private InlineKeyboardMarkup keyboardM1;
    private InlineKeyboardMarkup keyboardMLive;
    private InlineKeyboardMarkup keyboardMBack;

    public static Map<TournamentEnum, InlineKeyboardMarkup> keyboardM2 = new HashMap<>();
    static String mainMenuText = "<b>Sport Score Bot</b>\nВыберите турнир:";


    SportScoreParser ssParser;

    public SSBot() {
        ssParser = new SportScoreParser();
        //ssParser.startParsingLastNext();
 //       ssParser.startParsingTodayMatches();
 //       ssParser.startParsingAllMatches();
//        ssParser.startParsingTodayMatches();

        ScheduledExecutorService executorService;
        executorService = Executors.newSingleThreadScheduledExecutor();


        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ssParser.startParsingTodayMatches();
            }
        }, 0, 2, TimeUnit.MINUTES);

        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ssParser.startParsingLastNext();
                ssParser.startParsingAllMatches();
            }
        }, 0, 30, TimeUnit.MINUTES);


        setBotCommands();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<List<InlineKeyboardButton>> liveButtons = new ArrayList<>();
        List<List<InlineKeyboardButton>> backButtons = new ArrayList<>();

        List<List<InlineKeyboardButton>> subButtons;

        for (TournamentEnum tour : TournamentEnum.values()) {
            // Добавляем турниры в меню
            buttons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text(ssParser.getTourName(tour, true))
                                    .callbackData(tour.name()+"-"+"main")
                                    .build()
                    )
            );

            // Для каждого турнира у нас будет своё подменю, формируем его
            subButtons = new ArrayList<>();
            subButtons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text("✅   Прошедшие")
                                    .callbackData(tour.name()+"-"+"last")
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("⚡   СЕГОДНЯ")
                                    .callbackData(tour.name()+"-"+"today")
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("\uD83D\uDD1C   Ближайшие")
                                    .callbackData(tour.name()+"-"+"next")
                                    .build()
                    )
            );
            // И обязательно кнопку на возврат к списку турниров
            subButtons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text("⬅   Вернуться к списку турниров")
                                    .callbackData("menu")
                                    .build()
                    )
            );

            keyboardM2.put(tour, InlineKeyboardMarkup.builder().keyboard(subButtons).build());
        }

        buttons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⚡ Игры на сегодня")
                                .callbackData("today")
                                .build()
                )
        );

        buttons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("🔴 LIVE-результаты")
                                .callbackData("live")
                                .build()
                )
        );

        keyboardM1 = InlineKeyboardMarkup.builder()
                .keyboard(buttons).build();

        liveButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDD04   Обновить")
                                .callbackData("live-refresh")
                                .build()
                )
        );

        liveButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⬅   Вернуться к списку турниров")
                                .callbackData("menu")
                                .build()
                )
        );

        keyboardMLive = InlineKeyboardMarkup.builder()
                .keyboard(liveButtons).build();

        backButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⬅   Вернуться к списку турниров")
                                .callbackData("menu")
                                .build()
                )
        );

        keyboardMBack = InlineKeyboardMarkup.builder()
                .keyboard(backButtons).build();

        System.out.println("Bot has started");
    }

    public void setBotCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/menu", "Отобразить список турниров"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
        }
    }

    @Override
    public String getBotUsername() {
        return "Sport Score Bot";
    }

    @Override
    //public String getBotToken() { return "6148974109:AAGGus2GIIykwn6bmSLJyi5sFJJOOdr5npU"; } // Реальный токен
    public String getBotToken() { return "5885858281:AAETXWUlErRTVmFga-msU4hsxT5F5iOmu7A"; } // Тестовый токен

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage()) {
            var msg = update.getMessage();
            var user = msg.getFrom();
            var id = user.getId();
            var txt = msg.getText();

            if(msg.isCommand()) {
                if (txt.equals("/menu") || txt.equals("/start"))
                    sendMenu(id, mainMenuText, keyboardM1);
                return;
            }
        }
        else if(update.hasCallbackQuery()) {
            CallbackQuery cb = update.getCallbackQuery();
            var user = cb.getFrom();
            var id = user.getId();
            var queryId = cb.getId();
            var data = cb.getData();
            var msg = cb.getMessage();
            var msgId = msg.getMessageId();
            String chatId = String.valueOf(cb.getMessage().getChatId());

            buttonTap(id, queryId, data, msgId, chatId);
        }
    }

    public void sendText(Long who, String what){
        SendMessage message = new SendMessage();
        message.setChatId(who.toString());
        message.setText(what);
        message.setDisableNotification(false);
        message.setParseMode(ParseMode.HTML);

        try{
            execute(message);
        }catch (TelegramApiException e){
            e.printStackTrace();
        }
    }

    public void sendMenu(Long who, String txt, InlineKeyboardMarkup kb){
        SendMessage sm = SendMessage.builder().chatId(who.toString())
                .parseMode("HTML").text(txt)
                .replyMarkup(kb).build();

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void buttonTap(Long id, String queryId, String data, int msgId, String chatId) {
        /*
        EditMessageText newTxt = EditMessageText.builder()
                .chatId(id.toString())
                .messageId(msgId).text("").build();

        EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                .chatId(id.toString()).messageId(msgId).build();

        if(data.equals("next")) {
            newTxt.setText("MENU 2");
            newKb.setReplyMarkup(keyboardM2);
        } else if(data.equals("back")) {
            newTxt.setText("MENU 1");
            newKb.setReplyMarkup(keyboardM1);
        }
        */

        DeleteMessage deleteMessage = new DeleteMessage(chatId, msgId);

        AnswerCallbackQuery close = AnswerCallbackQuery.builder()
                .callbackQueryId(queryId).build();

        if(data.equals("menu")) {
            try {
                execute(close);
                execute(deleteMessage);
                sendMenu(id, mainMenuText, keyboardM1);
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
        else if(data.equals("today")) {
            try {
                execute(close);
                execute(deleteMessage);
                sendText(id, ssParser.getTodayMatches(false));
                sendMenu(id, "Варианты действий:", keyboardMBack);
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
        else if(data.equals("live")) {
            try {
                execute(close);
                execute(deleteMessage);
                sendText(id, ssParser.getTodayMatches(true));
                sendMenu(id, "Варианты действий:", keyboardMLive);
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
        else if(data.equals("live-refresh")) {
            try {
                EditMessageText newTxt = EditMessageText.builder()
                        .chatId(id.toString())
                        .parseMode("HTML")
                        .messageId(msgId).text(ssParser.getTodayMatches(true)).build();

                EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                        .chatId(id.toString()).messageId(msgId).build();
                newKb.setReplyMarkup(keyboardMLive);

                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(queryId);
                answer.setText("Результаты обновлены!");
                answer.setShowAlert(false);

                execute(newTxt);
                execute(newKb);
                execute(answer);
                execute(close);
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
        else {
            try {
                execute(close);
                execute(deleteMessage);
                //execute(newTxt);
                //execute(newKb);
                ArrayList<Object> info = ssParser.getTournamentActualMatches(data);
                TournamentEnum tour = (TournamentEnum) info.get(0);

                /*
                EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                        .chatId(id.toString()).messageId(msgId).build();
                newKb.setReplyMarkup(keyboardM2.get(tour));
                */
                String text = info.get(1).toString();
                sendText(id, text);
                sendMenu(id, "Отобразить другие матчи турнира:", keyboardM2.get(tour));
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
    }
}
