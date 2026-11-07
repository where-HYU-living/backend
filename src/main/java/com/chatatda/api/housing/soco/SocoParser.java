package com.chatatda.api.housing.soco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class SocoParser {

    private static final String GENERAL_SUPPLY = "일반공급";
    private static final String YOUTH = "청년";
    private static final Pattern XPOS = Pattern.compile("var\\s+xpos\\s*=\\s*\"([\\d.]+)\"");
    private static final Pattern YPOS = Pattern.compile("var\\s+ypos\\s*=\\s*\"([\\d.]+)\"");
    private static final Pattern AREA = Pattern.compile("\\((\\d+(?:\\.\\d+)?)\\)");

    private final ObjectMapper objectMapper;

    public SocoParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SocoHome> homes(String json) {
        List<SocoHome> homes = new ArrayList<>();
        for (JsonNode row : readTree(json).path("resultList")) {
            String code = text(row, "homeCode");
            if (code != null) {
                homes.add(new SocoHome(code, text(row, "homeName"), text(row, "adres"), text(row, "adresGu")));
            }
        }
        return homes;
    }

    public int noticePages(String json) {
        return readTree(json).path("pagingInfo").path("totPage").asInt(0);
    }

    public List<SocoNotice> notices(String json) {
        List<SocoNotice> notices = new ArrayList<>();
        for (JsonNode row : readTree(json).path("resultList")) {
            long boardId = row.path("boardId").asLong(0);
            String title = text(row, "nttSj");
            if (boardId == 0 || title == null) {
                continue;
            }
            String content = text(row, "content");
            String body = content == null ? "" : Jsoup.parse(content).text();
            notices.add(new SocoNotice(boardId, title.trim(), date(text(row, "optn1")), body));
        }
        return notices;
    }

    public SocoHomeDetail detail(String html) {
        Document doc = Jsoup.parse(html);
        return new SocoHomeDetail(number(YPOS, html), number(XPOS, html),
                labelled(doc, "대표전화"), homepage(doc), generalYouthUnits(doc), floorplanImages(doc));
    }

    private static List<SocoUnit> generalYouthUnits(Document doc) {
        Element heading = doc.select("div").stream()
                .filter(d -> d.ownText().trim().startsWith(GENERAL_SUPPLY))
                .findFirst().orElse(null);
        if (heading == null) {
            return List.of();
        }
        Element table = heading.nextElementSibling() == null ? null : heading.nextElementSibling().selectFirst("table");
        if (table == null) {
            return List.of();
        }
        List<SocoUnit> units = new ArrayList<>();
        for (Element tr : table.select("tbody tr")) {
            Elements td = tr.select("td");
            if (td.size() < 7 || !YOUTH.equals(td.get(2).text().trim())) {
                continue;
            }
            Long deposit = money(td.get(4).text());
            Long rent = money(td.get(5).text());
            if (deposit == null || rent == null) {
                continue;
            }
            String name = td.get(1).text().trim();
            Matcher area = AREA.matcher(name);
            units.add(new SocoUnit(name, td.get(0).text().trim(), area.find() ? Double.valueOf(area.group(1)) : null,
                    deposit, rent, money(td.get(6).text())));
        }
        return units;
    }

    private static List<String> floorplanImages(Document doc) {
        return doc.select("img[alt*=_평면도_]").stream().map(img -> img.attr("src").trim()).filter(s -> !s.isEmpty()).toList();
    }

    private static String labelled(Document doc, String label) {
        for (Element strong : doc.select("p > strong")) {
            if (strong.text().startsWith(label)) {
                String value = strong.parent().ownText().trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static String homepage(Document doc) {
        for (Element strong : doc.select("p > strong")) {
            if (strong.ownText().startsWith("홈페이지")) {
                Element a = strong.selectFirst("a[href]");
                return a == null ? null : a.attr("href").trim();
            }
        }
        return null;
    }

    private static Long money(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Long.valueOf(digits);
    }

    private static Double number(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new SocoApiException("soco 응답을 읽지 못했어요.", e);
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
