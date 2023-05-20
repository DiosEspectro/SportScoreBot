package org.diosespectro;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SSBot extends TelegramLongPollingBot {
    static Object Users;
    public String BotName = "";
    public String BotToken = "";
    static String[] BotAdmins;

    private InlineKeyboardMarkup keyboardM1;
    private InlineKeyboardMarkup keyboardMLive;
    private InlineKeyboardMarkup keyboardMTours;
    private InlineKeyboardMarkup keyboardMBack;

    public static Map<String, InlineKeyboardMarkup> keyboardM2 = new HashMap<>();
    static String mainMenuText;

    static SportScoreParser ssParser;
    String UserFile = SportScoreParser.baseFolder + "users.json";

    public SSBot() {
        ssParser = new SportScoreParser();
        loadSettings();
        mainMenuText = ssParser.getMessageHeader("⛳", BotName, "Главное меню:");

        ScheduledExecutorService executorService;
        executorService = Executors.newSingleThreadScheduledExecutor();

        // Напоминание от бота, что он в сети:
        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendMessage2Admins("Я онлайн");
            }
        }, 0, 6, TimeUnit.HOURS);

        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ssParser.loadTournamentsInfo();
            }
        }, 0, 5, TimeUnit.MINUTES);

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
        initMenus();
        initUsersFile();

        sendMessage2Admins("Бот запущен");
        System.out.println("Bot has started");
    }

    void initUsersFile(){
        try{
            JSONParser parser = new JSONParser();
            Users = parser.parse(new FileReader(SportScoreParser.baseFolder + "users.json"));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    void initMenus(){
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<List<InlineKeyboardButton>> tourButtons = new ArrayList<>();
        List<List<InlineKeyboardButton>> liveButtons = new ArrayList<>();
        List<List<InlineKeyboardButton>> backButtons = new ArrayList<>();

        List<List<InlineKeyboardButton>> subButtons;

        //for (TournamentEnum tour : TournamentEnum.values()) {
        for (String tour : SportScoreParser.Tournaments.keySet()) {
            //String tourName = tour.get(key)[1];

            // Добавляем турниры в меню
            tourButtons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text(ssParser.getTourName(tour, true))
                                    .callbackData(tour.toLowerCase() + "-" + "main")
                                    .build()
                    )
            );

            // Для каждого турнира у нас будет своё подменю, формируем его
            subButtons = new ArrayList<>();
            subButtons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text("✅   Прошедшие")
                                    .callbackData(tour.toLowerCase()+"-"+"last")
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("⚡   СЕГОДНЯ")
                                    .callbackData(tour.toLowerCase()+"-"+"today")
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("\uD83D\uDD1C   Ближайшие")
                                    .callbackData(tour.toLowerCase()+"-"+"next")
                                    .build()
                    )
            );
            // И обязательно кнопку на возврат к списку турниров
            subButtons.add(
                    Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text("⬅   Вернуться к списку турниров")
                                    .callbackData("tours")
                                    .build()
                    )
            );

            keyboardM2.put(tour, InlineKeyboardMarkup.builder().keyboard(subButtons).build());
        }

        tourButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⬅   Вернуться в меню")
                                .callbackData("menu")
                                .build()
                )
        );
        keyboardMTours = InlineKeyboardMarkup.builder()
                .keyboard(tourButtons).build();


        buttons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("\uD83C\uDFC6  Турниры")
                                .callbackData("tours")
                                .build()
                )
        );

        buttons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⚡  Игры на сегодня")
                                .callbackData("today")
                                .build()
                )
        );

        buttons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("🔴  LIVE-результаты")
                                .callbackData("live")
                                .build()
                )
        );

        keyboardM1 = InlineKeyboardMarkup.builder()
                .keyboard(buttons).build();

        liveButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⬅   Вернуться в меню")
                                .callbackData("menu")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDD04   Обновить")
                                .callbackData("live-refresh")
                                .build()
                )
        );
        keyboardMLive = InlineKeyboardMarkup.builder()
                .keyboard(liveButtons).build();

        backButtons.add(
                Arrays.asList(
                        InlineKeyboardButton.builder()
                                .text("⬅   Вернуться в меню")
                                .callbackData("menu")
                                .build()
                )
        );

        keyboardMBack = InlineKeyboardMarkup.builder()
                .keyboard(backButtons).build();
    }

    void loadSettings(){
        try {
            URL url = new File(SportScoreParser.baseFolder + "bot-settings.json").toURI().toURL();
            JSONParser parser = new JSONParser();

            InputStreamReader inputStreamReader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
            Object obj = parser.parse(inputStreamReader);
            inputStreamReader.close();

            JSONObject jsonObject = (JSONObject) obj;
            BotName = (String) jsonObject.get("bot-name");
            BotToken = (String) jsonObject.get("bot-token");
            JSONArray admins = (JSONArray) jsonObject.get("bot-admins");
            int i = -1;
            BotAdmins = new String[admins.size()];
            for(Object o:admins) {
                i++;
                BotAdmins[i] = (String) o;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setBotCommands() {
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/menu", "Главное меню"));
        listOfCommands.add(new BotCommand("/tours", "Список турниров"));
        listOfCommands.add(new BotCommand("/today", "Игры на сегодня"));
        listOfCommands.add(new BotCommand("/live", "Live-результаты"));
        listOfCommands.add(new BotCommand("/about", "Информация о боте"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
        }
    }

    @Override
    public String getBotUsername() {
        return BotName;
    }

    @Override
    //public String getBotToken() { return "6148974109:AAGGus2GIIykwn6bmSLJyi5sFJJOOdr5npU"; } // Реальный токен
    public String getBotToken() { return BotToken; } // Тестовый токен

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage()) {
            var msg = update.getMessage();
            var user = msg.getFrom();
            var id = user.getId();
            var userName = user.getUserName();
            var userFN = user.getFirstName();
            var userLN = user.getLastName();
            var txt = msg.getText();

            if(msg.isCommand()) {
                if (txt.equals("/menu") || txt.equals("/start")) {
                    updateUser(id, userName, userFN, userLN);
                    sendMenu(id, mainMenuText, keyboardM1);
                } else if (txt.equals("/tours") || txt.equals("/today") || txt.equals("/live") || txt.equals("/about")) {
                    updateUser(id, userName, userFN, userLN);
                    runContent(id, txt.replace("/", ""));
                } else if(txt.equals("/stat")){
                    if(userIsAdmin(id)){
                        getStatPage(id);
                    }
                }
                return;
            }
        }
        else if(update.hasCallbackQuery()) {
            CallbackQuery cb = update.getCallbackQuery();
            var user = cb.getFrom();
            var userName = user.getUserName();
            var userFN = user.getFirstName();
            var userLN = user.getLastName();
            var id = user.getId();
            var queryId = cb.getId();
            var data = cb.getData();
            var msg = cb.getMessage();
            var msgId = msg.getMessageId();
            String chatId = String.valueOf(cb.getMessage().getChatId());

            if(data.equals("menu")) updateUser(id, userName, userFN, userLN);

            buttonTap(id, queryId, data, msgId, chatId);
        }
    }

    public void updateUser(Long id, String userName, String userFN, String userLN){
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timeStamp = sdf.format(date);

        try{
            JSONParser parser = new JSONParser();
            Users = parser.parse(new FileReader(UserFile));
        }catch (Exception e){
            e.printStackTrace();
        }

        String userN ;
        if(userName != null) userN = userName;
            else if(userFN != null) userN = userFN + (userLN != null ? " " + userLN : "");
                else if(userLN != null) userN = userLN;
                    else userN = "<unknown>";

        JSONObject jsonObject = (JSONObject) Users;
        JSONObject jsonUsers = (JSONObject) jsonObject.get("users");
        JSONObject userInfo = (JSONObject) jsonUsers.get(Long.toString(id));
        if(userInfo == null) {
            JSONObject newUser = new JSONObject();
            newUser.put("username", userN);
            newUser.put("last-action", timeStamp);
            newUser.put("subscribe", 0);
            newUser.put("template", 1);

            jsonUsers.put(Long.toString(id), newUser);
            usersFileUpdate(UserFile);
            sendMessage2Admins("Новый пользователь: <b>" + userName + "</b>\nВсего пользователей: " + (jsonUsers.size()) + "\n#newuser");
        }
        else {
            String user = (String) userInfo.get("username");
            String lastaction = (String) userInfo.get("last-action");
            userInfo.replace("username", userN);
            userInfo.replace("last-action", timeStamp);
            usersFileUpdate(UserFile);
        }
    }

    static void usersFileUpdate(String userFile){
        try {
            JSONObject usersFile = (JSONObject) Users;
            FileWriter file = new FileWriter(userFile);
            file.write(usersFile.toJSONString());
            file.flush();
            file.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    static boolean userIsAdmin(Long id){
        return Arrays.asList(BotAdmins).contains(Long.toString(id));
    }

    void getStatPage(Long id){
        String message = ssParser.getMessageHeader("\uD83D\uDCDD", "Статистика", "");

        try{
            JSONParser parser = new JSONParser();
            Users = parser.parse(new FileReader(UserFile));
        }catch (Exception e){
            e.printStackTrace();
        }

        JSONObject jsonObject = (JSONObject) Users;
        JSONObject jsonUsers = (JSONObject) jsonObject.get("users");

        int usersCount = jsonUsers.size();
        message += "\nВсего пользователей: " + usersCount;

        // Покажем последних активных пользователей
        String[][] users = new String[usersCount][2];

        int i=-1;
        for(Object o:jsonUsers.values()) {
            JSONObject user = (JSONObject) o;
            String userName = (String) user.get("username");
            String lastAction = (String) user.get("last-action");
            i++;
            users[i][0] = userName;
            users[i][1] = lastAction;
        }

        Arrays.sort(users, Comparator.comparing(entry -> entry[1])); // Сортировка по времени (lastaction)

        int from = usersCount-1;
        int to = usersCount-11;
        if(to < 0) to = 0;

        message += "\n\nПоследние активные пользователи:";
        for(int u=from; u>=to;u--){
            String[] datetime = users[u][1].split(" ");
            message += "\n— " + users[u][0] + "    (" + SportScoreParser.getNormDate(datetime[0]) + ", " + datetime[1]+ ")";
        }

        sendText(id, message);
    }

    void sendMessage2Admins(String message){
        for (String idTxt : BotAdmins) {
            Long id = Long.parseLong(idTxt);
            this.sendText(id, message);
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
        else if(data.equals("tours") || data.equals("today") || data.equals("live")) {
            try {
                execute(close);
                execute(deleteMessage);
                runContent(id, data);
                //sendMenu(id, ssParser.getMessageHeader("\uD83C\uDFC6", "Турниры", "Выберите турнир:"), keyboardMTours);
            }catch(TelegramApiException e){
                e.printStackTrace();
            }
        }
        /*
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
        }*/
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
                String tour = (String) info.get(0);

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

    private void runContent(Long id, String data){
        switch (data) {
            case "menu" -> sendMenu(id, mainMenuText, keyboardM1);
            case "tours" -> sendMenu(id, ssParser.getMessageHeader("\uD83C\uDFC6", "Турниры", "Выберите турнир:"), keyboardMTours);
            case "today" -> {
                sendText(id, ssParser.getTodayMatches(false));
                sendMenu(id, "Варианты действий:", keyboardMBack);
            }
            case "live" -> {
                sendText(id, ssParser.getTodayMatches(true));
                sendMenu(id, "Варианты действий:", keyboardMLive);
            }
            case "about" -> {
                sendText(id, ssParser.getMessageHeader("❔", "О боте", "") +
                            "\nДанный бот показывает результаты только тех турниров, которые перечислены в разделе \"Турниры\". " +
                            "В будущем будут добавлены дополнительные популярные турниры.");
                sendMenu(id, "Варианты действий:", keyboardMBack);
            }
        }
    }
}
