package com.chatatda.api.realdeal;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public class RealDealNormalizer {

    private static final Set<String> OK_CODES = Set.of("000", "00");
    private static final long MANWON = 10_000L;

    public record MolitPage(int totalCount, int itemCount, int skipped, List<RealDeal> deals) {
    }

    public MolitPage parse(String xml, RealDealType type, Instant ingestedAt) {
        Element root = parseXml(xml).getDocumentElement();

        if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
            throw new MolitApiException(text(root, "returnReasonCode") + ": " + text(root, "returnAuthMsg")
                    + " (" + text(root, "errMsg") + ")");
        }
        String resultCode = text(root, "resultCode");
        if (resultCode == null || !OK_CODES.contains(resultCode)) {
            throw new MolitApiException(resultCode + ": " + text(root, "resultMsg"));
        }

        NodeList items = root.getElementsByTagName("item");
        List<RealDeal> deals = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < items.getLength(); i++) {
            RealDeal deal = toDeal(fields((Element) items.item(i)), type, ingestedAt);
            if (deal == null) {
                skipped++;
            } else {
                deals.add(deal);
            }
        }
        String total = text(root, "totalCount");
        return new MolitPage(total == null ? 0 : Integer.parseInt(total), items.getLength(), skipped, deals);
    }

    private RealDeal toDeal(Map<String, String> f, RealDealType type, Instant ingestedAt) {
        try {
            String dong = f.get("umdNm");
            String lawdCd = f.get("sggCd");
            double area = Double.parseDouble(f.get(type == RealDealType.DETACHED ? "totalFloorAr" : "excluUseAr"));
            long deposit = manwon(f.get("deposit"));
            long rent = manwon(f.get("monthlyRent"));
            YearMonth ym = YearMonth.of(Integer.parseInt(f.get("dealYear")), Integer.parseInt(f.get("dealMonth")));
            if (dong == null || lawdCd == null || area <= 0 || deposit < 0 || rent < 0) {
                return null;
            }
            return new RealDeal(lawdCd, type, dong, area, deposit, rent, ym,
                    integerOrNull(f.get("floor")), integerOrNull(f.get("buildYear")), f.get("contractType"), ingestedAt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long manwon(String value) {
        return Long.parseLong(value.replace(",", "").trim()) * MANWON;
    }

    private static Integer integerOrNull(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private static Map<String, String> fields(Element item) {
        Map<String, String> map = new HashMap<>();
        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String value = child.getTextContent().trim();
                map.put(child.getNodeName(), value.isEmpty() ? null : value);
            }
        }
        return map;
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new MolitApiException("국토부 응답을 해석할 수 없어요.", e);
        }
    }
}
