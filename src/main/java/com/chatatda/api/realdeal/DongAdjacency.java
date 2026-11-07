package com.chatatda.api.realdeal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DongAdjacency {

    private static final String[][] PAIRS = {
            {"행당동", "사근동"}, {"행당동", "마장동"}, {"행당동", "응봉동"}, {"행당동", "하왕십리동"}, {"행당동", "도선동"},
            {"사근동", "마장동"}, {"사근동", "용답동"},
            {"마장동", "하왕십리동"}, {"마장동", "용답동"}, {"마장동", "답십리동"},
            {"홍익동", "도선동"}, {"홍익동", "상왕십리동"}, {"홍익동", "하왕십리동"},
            {"도선동", "하왕십리동"},
            {"상왕십리동", "하왕십리동"},
            {"용답동", "답십리동"},
            {"답십리동", "용두동"}, {"답십리동", "전농동"},
    };

    private final Map<String, List<String>> neighbors = new LinkedHashMap<>();

    public DongAdjacency() {
        for (String[] pair : PAIRS) {
            link(pair[0], pair[1]);
            link(pair[1], pair[0]);
        }
    }

    private void link(String from, String to) {
        List<String> list = neighbors.computeIfAbsent(from, k -> new ArrayList<>());
        if (!list.contains(to)) {
            list.add(to);
        }
    }

    public List<String> neighbors(String dong) {
        return List.copyOf(neighbors.getOrDefault(dong, List.of()));
    }

    public Set<String> dongs() {
        return Set.copyOf(neighbors.keySet());
    }
}
