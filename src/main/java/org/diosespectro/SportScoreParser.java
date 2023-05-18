package org.diosespectro;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SportScoreParser {
    static Integer curYear = getCurYear();
    public static String baseFolder = "ssb_data/";
    public static Map<TournamentEnum, String> TourNames = new HashMap<>();

    public SportScoreParser() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
        TimeZone.getDefault();

        checkFolder();
        initTourNames();
    }

    static String getBasicUrl(){ return "https://www.championat.com/"; }

    static void checkFolder(){
        String filePathStr = baseFolder;
        Path filePath = Paths.get(filePathStr);
        if (!Files.exists(filePath)) {
            File newDir = new File(filePathStr);

            if(newDir.mkdir()){
                System.out.println("Папка " + baseFolder + " создана");
            }
        }
    }

    public static String getTodayDate(){
        Date dateNow = new Date();
        SimpleDateFormat formatForDate = new SimpleDateFormat("yyyy-MM-dd");
        return formatForDate.format(dateNow);
    }
    static Integer getCurYear(){
        String today = getTodayDate();
        String[] todayA = today.split("-");
        return Integer.parseInt(todayA[0]);
    }

    static URL getTodayUrl(boolean isLocal){
        URL url = null;

        try {
            if(isLocal) url = new File(baseFolder + "today-matches.json").toURI().toURL();
                else url = new URL(getBasicUrl() + "stat/" + getTodayDate() + ".json");
        }catch(MalformedURLException e){
            e.printStackTrace();
        }
        return url;
    }

    public void startParsingLastNext(){
        curYear = getCurYear();
        for(TournamentEnum tour : TournamentEnum.values()){
            String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
            parseTourAllMatches(tour, CalendarDataType.LAST, tourInfo);
            parseTourAllMatches(tour, CalendarDataType.NEXT, tourInfo);
        }
    }

    public void startParsingAllMatches(){
        for (TournamentEnum tour : TournamentEnum.values()) {
            String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
            parseTourAllMatches(tour, CalendarDataType.ALL, tourInfo);
        }
    }

    public void startParsingTodayMatches(){
        downloadTodayFile();
        for (TournamentEnum tour : TournamentEnum.values()) {
            parseTourTodayMatches(tour);
        }
    }

    static void downloadTodayFile(){
        try {
            String todayFile = baseFolder + "today-matches.json";
            URL url = getTodayUrl(false);
            InputStream inputStream = url.openStream();
            Files.copy(inputStream, Paths.get(todayFile), StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    static void parseTourAllMatches(TournamentEnum tour, CalendarDataType dType, String[] tourInfo){
        String url;
        String dataType = "";

        if(dType == CalendarDataType.ALL)
            url = getBasicUrl()+getSportNameByTour(tour).toString().toLowerCase()+"/"+getTourChampId(tour)+"/tournament/"+tourInfo[1]+"/calendar/";
        else {
            url = getBasicUrl() + getSportNameByTour(tour).toString().toLowerCase() + "/" + getTourChampId(tour) + ".html";
            dataType = dType.toString().toLowerCase();
        }

        try {
            Document doc = Jsoup.connect(url).get();
            //Elements elms = doc.select("table.stat-results__table > tbody > tr"); // Получаем список матчей

            Elements elms;

            // Получаем список матчей
            if(dType == CalendarDataType.ALL)
                elms = doc.select("table.stat-results__table > tbody > tr");
            else
                elms = doc.select("div._accordion[data-type="+dataType+"] > table.stat-results__table > tbody > tr"); // Получаем список матчей

            int matchesCount = elms.size();
            String[][] matches = new String[matchesCount][21];

            for(int i=0;i<matchesCount;i++){
                String strtmp;
                String[] strsplit;

                Element matchRaw = elms.get(i); // Необработанная строка с матчем
                //System.out.println(matchRaw.attributes().get("data-team"));

                if(dType == CalendarDataType.ALL) {
                    // Сперва запишем параметры, которые можем получить из аттрибутов заглавной строки матча
                    matches[i][2] = matchRaw.attributes().get("data-round"); // ID этапа турнира
                    matches[i][3] = matchRaw.attributes().get("data-tour"); // № тура
                    matches[i][4] = matchRaw.attributes().get("data-month"); // Год_месяц матча
                    matches[i][5] = matchRaw.attributes().get("data-team").split("/")[0]; // Id домашней команды
                    matches[i][6] = matchRaw.attributes().get("data-team").split("/")[1]; // Id гостевой команды
                }
                matches[i][7] = matchRaw.attributes().get("data-played"); // Признак - сыгран ли матч (0 - нет, 1 - да)

                // Теперь идём вглубь, в столбцы
                Element tmp = matchRaw.selectFirst("tr > td.stat-results__fav");
                if(tmp != null) matches[i][0] = tmp.attributes().get("data-type").trim(); // Тип данных (например, match)

                tmp = matchRaw.selectFirst("tr > td.stat-results__fav");
                if(tmp != null) matches[i][1] = tmp.attributes().get("data-id").trim(); // ID матча

                tmp = matchRaw.selectFirst("tr > td.stat-results__link > a");
                if(tmp != null) matches[i][8] = tmp.attributes().get("href").trim(); // Ссылка на матч

                tmp = matchRaw.selectFirst("tr > td.stat-results__group");
                if(tmp != null) matches[i][9] = tmp.text().trim(); // Название группы

                tmp = matchRaw.selectFirst("tr > td.stat-results__tour-num");
                if(tmp != null) matches[i][10] = tmp.text().trim(); // Номер тура

                // Далее обрабатываем дату и время матча
                tmp = matchRaw.selectFirst("tr > td.stat-results__date-time");
                if(tmp != null)
                {
                    strtmp = tmp.text().trim(); // Строка с датой и временем
                    strsplit = strtmp.replace("                            &nbsp;", " ").split(" ");

                    matches[i][11] = transformDate(strsplit[0]); // Дата матча
                    if(strsplit.length > 1) matches[i][12] = strsplit[1]; // Время матча
                }

                // Далее обрабатываем названия команд
                Elements tmps = matchRaw.select("span.table-item__name");
                if(tmps.size() == 2) {
                    matches[i][13] = tmps.get(0).text(); // Название команды 1
                    matches[i][14] = tmps.get(1).text(); // Название команды 2
                }

                // Далее счёт, если есть (если есть признак сыгранного матча)
                if(matches[i][7].equals("1")) {
                    tmp = matchRaw.selectFirst("tr > td.stat-results__count > a > span.stat-results__count-main");
                    if (tmp != null) {
                        strsplit = tmp.text().trim().split(":");
                        if (strsplit.length == 2) {
                            matches[i][15] = strsplit[0].trim(); // Забитые голы первой команды
                            matches[i][16] = strsplit[1].trim(); // Забитые голы второй команды
                        }
                    }

                    tmp = matchRaw.selectFirst("tr > td.stat-results__count > a > span.stat-results__count-ext");
                    if (tmp != null) {
                        strsplit = tmp.text().trim().split(":");
                        if (strsplit.length == 2) {
                            matches[i][17] = strsplit[0].trim(); // Экстра-голы первой команды
                            matches[i][18] = strsplit[1].trim(); // Экстра-голы второй команды
                        } else {
                            matches[i][17] = strsplit[0].trim(); // Если это не счёт, а просто текст или символы
                            matches[i][18] = strsplit[0].trim(); // То записываем их в обе ячейки
                        }
                    }
                }
            }

            saveMatchesToXML(tour, tourInfo, matches, false, dType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String getFilePath(TournamentEnum tour, boolean onlyToday, CalendarDataType dType){
        String fileName = SportScoreParser.getSportNameByTour(tour).toString().toLowerCase() + "_" + tour.toString().toLowerCase();
        //if(onlyToday) fileName += "_" + getTodayDate();
        if(onlyToday) fileName += "_today";
        if(dType != CalendarDataType.ALL) fileName += "_" + dType.toString().toLowerCase();
        fileName +=  ".xml";

        return baseFolder + fileName;
    }

    static void saveMatchesToXML(TournamentEnum tour, String[] tourInfo, String[][] matches, boolean onlyToday, CalendarDataType dType){
        String filePath = getFilePath(tour, onlyToday, dType);

        org.w3c.dom.Element elmnt;
        org.w3c.dom.Element matchesroot;
        org.w3c.dom.Element match;

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Создание корневого элемента
            org.w3c.dom.Document doc = docBuilder.newDocument();
            org.w3c.dom.Element rootElement = doc.createElement("tournament");
            doc.appendChild(rootElement);

            // Далее прописываем параметры турнира (ID, название и.т.д)
            elmnt = doc.createElement("tourid");
            elmnt.appendChild(doc.createTextNode(tourInfo[0])); // Длинный ID
            rootElement.appendChild(elmnt);

            elmnt = doc.createElement("touridshort");
            elmnt.appendChild(doc.createTextNode(tourInfo[1])); // Короткий ID
            rootElement.appendChild(elmnt);

            elmnt = doc.createElement("tourname");
            elmnt.appendChild(doc.createTextNode(tourInfo[2])); // Название турнира
            rootElement.appendChild(elmnt);

            // Далее создаём коллекцию матчей
            matchesroot = doc.createElement("matches");
            rootElement.appendChild(matchesroot);

            // Далее добавляем каждый матч в отдельную группу
            for (String[] strings : matches) {
                if (dType == CalendarDataType.ALL && (strings[5].equals("0") || strings[6].equals("0")))
                    continue; // Если хотя бы одна команда не указана, то не записываем

                // Создаём матч
                match = doc.createElement("match");
                matchesroot.appendChild(match);

                // Далее добавляем все параметры матча внутрь матча
                for (int j = 0; j < 21; j++) {
                    if(dType != CalendarDataType.ALL && j >= 2 && j <= 6) continue;
                    String tagName = switch (j) {
                        case 0 -> "type";
                        case 1 -> "id";
                        case 2 -> "round";
                        case 3 -> "tour";
                        case 4 -> "month";
                        case 5 -> "team1id";
                        case 6 -> "team2id";
                        case 7 -> "played";
                        case 8 -> "link";
                        case 9 -> "group";
                        case 10 -> "tourstr";
                        case 11 -> "date";
                        case 12 -> "time";
                        case 13 -> "team1";
                        case 14 -> "team2";
                        case 15 -> "score1";
                        case 16 -> "score2";
                        case 17 -> "score1ext";
                        case 18 -> "score2ext";
                        case 19 -> "live";
                        case 20 -> "liveperiod";
                        default -> "";
                    };

                    //if (strings[j] != null) {
                        elmnt = doc.createElement(tagName);
                        elmnt.appendChild(doc.createTextNode(strings[j] != null ? strings[j] : "")); // ID
                        match.appendChild(elmnt);
                    //}
                }
            }

            // Запись XML-файла
            doc.setXmlStandalone(true);
            doc.normalizeDocument();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    static void parseTourTodayMatches(TournamentEnum tour) {
        String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
        SportNameEnum sportName = getSportNameByTour(tour);

        URL url = getTodayUrl(true);

        try{
            JSONParser parser = new JSONParser();

            InputStreamReader inputStreamReader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
            Object obj = parser.parse(inputStreamReader);
            inputStreamReader.close();

            JSONObject jsonObject = (JSONObject) obj;
            JSONObject matchesObj = (JSONObject) jsonObject.get("matches");
            if(matchesObj != null) {
                matchesObj = (JSONObject) matchesObj.get(sportName.toString().toLowerCase(Locale.ROOT));

                if(matchesObj != null) {
                    matchesObj = (JSONObject) matchesObj.get("tournaments");

                    if(matchesObj != null) {
                        matchesObj = (JSONObject) matchesObj.get(tourInfo[0]);

                        if(matchesObj != null) {
                            JSONArray elms = (JSONArray) matchesObj.get("matches");

                            int matchesCount = elms.size();

                            if (matchesCount > 0) {
                                String[][] matches = new String[matchesCount][21];
                                JSONObject match_more;
                                JSONArray match_arr;

                                // Поехали собирать информацию
                                int i = -1;
                                boolean isLive;

                                for(Object o:elms) {
                                    i++;
                                    isLive = false;

                                    JSONObject match = (JSONObject) o;
                                    matches[i][0] = "match"; // type
                                    matches[i][1] = match.get("data-id").toString(); // id

                                    match_more = (JSONObject) match.get("group");
                                    matches[i][2] = match_more.get("stage").toString() + "/" + match_more.get("id").toString(); // round
                                    matches[i][9] = match_more.get("name").toString(); // group

                                    try {
                                        matches[i][3] = match.get("tour").toString(); // tour
                                    }catch(NullPointerException e){
                                        //e.printStackTrace();
                                        // do nothing
                                    }

                                    matches[i][10] = matches[i][3]; // tourstr

                                    match_arr = (JSONArray) match.get("teams");
                                    match_more = (JSONObject) match_arr.get(0);
                                    matches[i][5] = match_more.get("id").toString(); // team1id
                                    matches[i][13] = match_more.get("name").toString(); // team1

                                    match_more = (JSONObject) match_arr.get(1);
                                    matches[i][6] = match_more.get("id").toString(); // team2id
                                    matches[i][14] = match_more.get("name").toString(); // team2

                                    match_more = (JSONObject) match.get("flags");
                                    matches[i][7] = match_more.get("is_played").toString(); // played
                                    if(matches[i][7].equals("0")) // если матч не завершён, проверяем - идёт-ли матч прямо сейчас
                                        isLive = ((long)match_more.get("live") != 0);

                                    matches[i][8] = match.get("link").toString(); // link

                                    matches[i][11] = match.get("date").toString(); // date
                                    matches[i][12] = match.get("time").toString(); // time

                                    String[] date = matches[i][11].split("-");
                                    matches[i][4] = date[0] + "_" + date[1]; // month

                                    if(matches[i][7].equals("1") || isLive) { // Если матч завершён, либо матч идёт прямо сейчас
                                        match_more = (JSONObject) match.get("result");
                                        match_more = (JSONObject) match_more.get("detailed");

                                        matches[i][15] = match_more.get("goal1").toString(); // score1
                                        matches[i][16] = match_more.get("goal2").toString(); // score2
                                        matches[i][17] = match_more.get("extra").toString(); // score1ext
                                        matches[i][18] = matches[i][17]; // score2ext

                                        if (isLive) {
                                            matches[i][19] = "1"; // live
                                            match_more = (JSONObject) match.get("status");
                                            matches[i][20] = match_more.get("name").toString(); // liveperiod
                                        }
                                    }
                                }

                                saveMatchesToXML(tour, tourInfo, matches, true, CalendarDataType.ALL);
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static String[] getCurTourInfo(TournamentEnum tour){
        // Возвращает массив строк, 0 - это длинный ID текущего турнира для Live-парсинга; 1 - это короткий ID для ссылок; 2 - Имя турнира
        String[] ret = new String[3];
        String url = getBasicUrl()+getSportNameByTour(tour).toString().toLowerCase()+"/"+getTourChampId(tour)+".html"; // Генерим ссылку

        try {
            Document doc = Jsoup.connect(url).timeout(10 * 10000).get();
            Elements elms = doc.select("div.tournament-top");
            String[] classes = elms.get(0).className().split(" ");
            ret[0] = classes[1].substring(1) ;// Длинный ID
            String[] values = classes[1].split("-");
            ret[1] = values[1]; // Короткий ID

            elms = doc.select("div.entity-header__title-name a");
            ret[2] = elms.get(0).text(); // Название турнира
        } catch (Exception e) {
            System.out.println();
            System.out.println("ErrorURL: " + url);
            System.out.println("----");
            e.printStackTrace();
        }

        return ret;
    }

    public static SportNameEnum getSportNameByTour(TournamentEnum tournament){
        return switch (tournament) {
            case RPL, CHAMPIONSLEAGUE, WORLDCUP -> SportNameEnum.FOOTBALL;
            case KHL, WHC -> SportNameEnum.HOCKEY;
        };
    }

    static String getTourChampId(TournamentEnum tournament){
        return switch (tournament) {
            case RPL -> "_russiapl";
            case CHAMPIONSLEAGUE -> "_ucl";
            case WORLDCUP -> "_worldcup";
            case KHL -> "_superleague";
            case WHC -> "_whc";
        };
    }

    static String transformDate(String date){
        String[] e = date.split("\\.");
        return e[2] + "-" + e[1] + "-" + e[0];
    }

    static String getNormDate(String date){
        String[] e = date.split("-");
        Calendar d = new GregorianCalendar(Integer.parseInt(e[0]), Integer.parseInt(e[1])-1, Integer.parseInt(e[2]));
        String month = d.getDisplayName(Calendar.MONTH, Calendar.LONG, new Locale("ru"));

        String showYear = "";
        int year = Integer.parseInt(e[0]);
        if(curYear != year) showYear = " " + year;

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date Ddate = null;

        try {
            Ddate = df.parse(date);
        } catch (ParseException j) {
            j.printStackTrace();
        }

       df = new SimpleDateFormat("EEEE", new Locale("ru"));
       String dayOfWeek = df.format(Ddate);

        return Integer.parseInt(e[2]) + " " + month + showYear + ", " + dayOfWeek;
    }

    public String getMessageHeader(String icon, String title, String caption){
        String ret = "";

        ret = "————————————————\n" + icon + "   <b>" + title + "</b>\n————————————————";
        if(!caption.isEmpty()) ret += "\n"+caption;

        return ret;
    }

    public String getTodayMatches(boolean getOnlyLive){
        String pageTitle;
        String pageIcon;
        String pageEmpty;

        if(getOnlyLive){
            pageTitle = "Live-результаты";
            pageIcon = "\uD83D\uDD34";
            pageEmpty = "В данный момент нет активных матчей";
        } else {
            pageTitle = "Игры на сегодня";
            pageIcon = "⚡";
            pageEmpty = "На сегодня игр не предусмотрено";
        }

        String ret = getMessageHeader(pageIcon, pageTitle, "");
        String file;
        String[][] matches;
        int mcount = 0;

        for (TournamentEnum tour : TournamentEnum.values()) {
            file = getFilePath(tour, true, CalendarDataType.ALL);

            if(!file.isEmpty()) {
                matches = getMatches(file, tour, getOnlyLive);
                int matchesCount = matches.length;

                if(matchesCount > 0) {
                    if(!ret.isEmpty()) ret += "\n\n";
                    ret += getTourName(tour, true);

                    for (String[] match : matches) {
                        mcount++;
                        ret += "\n " + getMatchLine(match);
                    }
                }
            }
        }

        if(mcount == 0) ret += "\n\n<i>" + pageEmpty + "</i>";
        return ret;
    }

    public ArrayList<Object> getTournamentActualMatches(String tourNameStr){
        String ret = "";
        String option = tourNameStr.split("-")[1];
        TournamentEnum tour = null;
        String todayDate = getTodayDate();
        String file = "";
        String[][] matches;
        String caption = "";

        tourNameStr = tourNameStr.split("-")[0];

        try {
            tour = TournamentEnum.valueOf(tourNameStr);
        }catch(IllegalArgumentException e){
            ret = "Ошибка выбора турнира";
        }

        if(tour != null) {
            switch(option){
                case "today": case "main":
                        file = getFilePath(tour, true, CalendarDataType.ALL);
                        caption = "Сегодняшняя программа";
                    break;
                case "last": case "mainlast":
                        file = getFilePath(tour, false, CalendarDataType.LAST);
                        caption = "Последние игры";
                    break;
                case "next": case "mainnext":
                        file = getFilePath(tour, false, CalendarDataType.NEXT);
                        caption = "Ближайшие игры";
                    break;
            }

            if(!file.isEmpty()) {
                matches = getMatches(file, tour, false);
                int matchesCount = matches.length;

                // Если матчей нет в разделе, то подгружаем сразу другой
                if(matchesCount == 0) {
                    String nomatchTxt = "Нет матчей для отображения";
                    ArrayList<Object> info;

                    switch (option){
                        case "main":
                                info = getTournamentActualMatches(tour.name()+"-mainlast");
                                ret = info.get(1).toString();
                            break;
                        case "mainlast":
                                info = getTournamentActualMatches(tour.name()+"-mainnext");
                                ret = info.get(1).toString();
                            break;
                        case "mainnext":
                                ret = getTourName(tour, true) + "\n<i>"+nomatchTxt+"</i>\n";
                            break;
                        default:
                            ret = getTourName(tour, true) + "\n<i>" + caption + "</i>\n\n<i>"+nomatchTxt+"</i>\n";
                            break;
                    }
                }
                else {
                    ret = getMessageHeader("", getTourName(tour, true), caption);

                    String oldDate = "";

                    for (String[] match : matches) {
                        if (!oldDate.equals(match[2])) {
                            if (!oldDate.isEmpty()) ret += "\n";
                            oldDate = match[2];
                            ret += "\n\uD83D\uDCC5  <u><b>" + (oldDate.equals(todayDate) ? "СЕГОДНЯ" : getNormDate(oldDate)) + "</b></u>";
                        }

                        ret += "\n " + getMatchLine(match);
                    }
                }
            }
        }

        ArrayList<Object> retlist = new ArrayList <>();
        retlist.add(tour);
        retlist.add(ret);

        return retlist;
    }

    static String[][] getMatches(String filePath, TournamentEnum tourE, boolean getOnlyLive){
        File f = new File(filePath);
        String[][] ret = new String[0][0];

        if(f.exists() && !f.isDirectory()) {
            org.w3c.dom.Document calendar = getXMLDocument(filePath);

            String tName = TourNames.get(tourE);
            if(tName == null) {
                NodeList tournameNodes = calendar.getElementsByTagName("tourname");
                String fileTName = tournameNodes.item(0).getTextContent();
                TourNames.put(tourE, fileTName);
            }

            NodeList nodeList = calendar.getElementsByTagName("match");

            ret = new String[nodeList.getLength()][12];

            int m = -1;
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) node;

                    String isLive =  element.getElementsByTagName("live").item(0).getTextContent();
                    if(getOnlyLive && isLive.isEmpty()) continue;

                    m++;

                    ret[m][0] = element.getElementsByTagName("id").item(0).getTextContent();
                    ret[m][1] = element.getElementsByTagName("group").item(0).getTextContent();
                    ret[m][2] = element.getElementsByTagName("date").item(0).getTextContent();
                    ret[m][3] = element.getElementsByTagName("time").item(0).getTextContent();
                    ret[m][4] = element.getElementsByTagName("team1").item(0).getTextContent();
                    ret[m][5] = element.getElementsByTagName("team2").item(0).getTextContent();
                    ret[m][6] = element.getElementsByTagName("score1").item(0).getTextContent();
                    ret[m][7] = element.getElementsByTagName("score2").item(0).getTextContent();
                    ret[m][8] = element.getElementsByTagName("score1ext").item(0).getTextContent();
                    ret[m][9] = element.getElementsByTagName("score2ext").item(0).getTextContent();
                    ret[m][10] = element.getElementsByTagName("live").item(0).getTextContent();
                    ret[m][11] = element.getElementsByTagName("liveperiod").item(0).getTextContent();
                }
            }

            if(getOnlyLive){
                // Создадим массив только для live-результатов
                String[][] liveMatches = new String[m+1][12];
                System.arraycopy(ret, 0, liveMatches, 0, m + 1);

                ret = liveMatches;
            }
        }

        return ret;
    }

    static String getMatchLine(String[] match){
        String time;
        String liveMark;
        String group = "";
        String matchTxt;
        String liveTxt = "";

        liveMark = !match[10].isEmpty() ? "\uD83D\uDD34" : "|"; // Live-иконка

        if(!match[3].isEmpty()) // Время матча
            time = match[3];
        else
            time = "--:--";

        if(!match[1].isEmpty()) group = match[1]; // Группа

        String score = " - ";

        if(!match[6].isEmpty() && !match[7].isEmpty()) { // Счёт
            score = "<code>" + match[6] + ":" + match[7] + "</code>";

            if(!match[8].isEmpty()) {
                if(!match[8].equals(match[9])){
                    score += " <i>(" + match[8] + ":"+ match[9] + ")</i>";
                } else score += " <i>(" + match[8] + ")</i>";
            }

            score = "  " + score + "  ";
        }

        matchTxt = "<b>" + match[4] + "</b>" + score + "<b>" + match[5] + "</b>"; // Строка с матчем и счётом

        if(!match[11].isEmpty()) liveTxt += match[11]; // Live-текст

        return String.format(getMatchLineTemplate(1, group, liveTxt), time, liveMark, group, matchTxt, liveTxt);
    }

    static String getMatchLineTemplate(int template, String group, String liveTxt){
        String ret = "%1$s %2$s %4$s  <i>%5$s</i>";

        if(!group.isEmpty()) {
            switch (template) {
                case 1 -> {
                    ret = "%1$s %2$s %3$s\n%4$s";
                    if (!liveTxt.isEmpty()) ret += "\n<i>%5$s</i>";
                    ret += "\n";
                }
                case 2 -> ret = "%1$s %2$s %4$s  <i>" + (!liveTxt.isEmpty() ? "%5$s " : "") + "(%3$s)</i>";
            }
        }

        return ret;
    }



    static org.w3c.dom.Document getXMLDocument(String filePath){
        org.w3c.dom.Document ret = null;

        try{
            File inputFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            ret = dBuilder.parse(inputFile);
            ret.getDocumentElement().normalize();
        }catch (Exception e){
            e.printStackTrace();
        }

        return ret;
    }

    static void initTourNames(){
        for(TournamentEnum tour : TournamentEnum.values()) {
            String filePath = getFilePath(tour, false, CalendarDataType.LAST);

            File f = new File(filePath);
            if(f.exists() && !f.isDirectory()) {
                org.w3c.dom.Document xmldoc = getXMLDocument(filePath);
                NodeList tournameNodes = xmldoc.getElementsByTagName("tourname");
                String fileTName = tournameNodes.item(0).getTextContent();
                TourNames.put(tour, fileTName);
            }
        }
    }

    public String getTourName(TournamentEnum tour, boolean withEmo){
        String ret = TourNames.get(tour);

        if(ret == null) ret = tour.toString();

        // Определим иконку для турнира
        if(withEmo) {
            String emo = "";
            SportNameEnum sport = getSportNameByTour(tour);

            if (sport == SportNameEnum.FOOTBALL) emo = "⚽";
                else emo = "\uD83C\uDFD2";

            ret = emo + "   " + ret;
        }

        return ret;
    }
}
