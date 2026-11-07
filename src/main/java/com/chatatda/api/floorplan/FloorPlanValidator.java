package com.chatatda.api.floorplan;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 평면도 JSON 의 구조 검사. AI 가 뽑은 초안이 3D 를 깨뜨리는 흔한 실수(벽 인덱스 범위 밖, 개구부가 벽 밖,
 * 방 꼭짓점 부족)만 잡는다. 선택 필드(kind·floor·fixtures·swing …)는 모양만 본다.
 * 좌표가 현실적으로 맞는지는 사람이 3D 로 보고 판단한다.
 */
public final class FloorPlanValidator {

    private static final double EPS = 0.02;
    private static final java.util.Set<String> FACINGS = java.util.Set.of("N", "E", "S", "W");

    private FloorPlanValidator() {
    }

    /** 문제 목록. 비어 있으면 통과 */
    public static List<String> validate(JsonNode fp) {
        List<String> errors = new ArrayList<>();
        if (!fp.isObject()) {
            return List.of("최상위가 객체가 아니에요.");
        }
        if (!fp.path("id").canConvertToLong() || !fp.path("id").isIntegralNumber()) {
            errors.add("id 는 정수여야 해요.");
        }
        if (!fp.path("name").isTextual() || fp.path("name").asText().isBlank()) {
            errors.add("name 이 비어 있어요.");
        }
        if (!positive(fp.path("areaM2"))) {
            errors.add("areaM2 는 0보다 커야 해요.");
        }
        if (!positive(fp.path("ceilingHeight"))) {
            errors.add("ceilingHeight 는 0보다 커야 해요.");
        }

        JsonNode walls = fp.path("walls");
        double[] lengths = new double[walls.isArray() ? walls.size() : 0];
        if (!walls.isArray() || walls.isEmpty()) {
            errors.add("walls 가 비어 있어요.");
        } else {
            for (int i = 0; i < walls.size(); i++) {
                JsonNode w = walls.get(i);
                if (!point(w.path("from")) || !point(w.path("to"))) {
                    errors.add("walls[" + i + "] from/to 는 [x,y] 숫자 쌍이어야 해요.");
                    continue;
                }
                lengths[i] = Math.hypot(w.path("to").get(0).asDouble() - w.path("from").get(0).asDouble(),
                        w.path("to").get(1).asDouble() - w.path("from").get(1).asDouble());
                if (lengths[i] < 1e-6) {
                    errors.add("walls[" + i + "] 길이가 0이에요.");
                }
                if (!positive(w.path("thickness"))) {
                    errors.add("walls[" + i + "].thickness 는 0보다 커야 해요.");
                }
                if (w.has("height") && !positive(w.path("height"))) {
                    errors.add("walls[" + i + "].height 는 0보다 커야 해요.");
                }
            }
        }

        JsonNode openings = fp.path("openings");
        if (!openings.isArray()) {
            errors.add("openings 가 배열이 아니에요.");
        } else {
            for (int i = 0; i < openings.size(); i++) {
                JsonNode o = openings.get(i);
                String at = "openings[" + i + "]";
                String type = o.path("type").asText();
                if (!type.equals("door") && !type.equals("window")) {
                    errors.add(at + ".type 은 door 또는 window 여야 해요.");
                }
                int wall = o.path("wall").asInt(-1);
                if (!o.path("wall").isIntegralNumber() || wall < 0 || wall >= lengths.length) {
                    errors.add(at + ".wall(" + o.path("wall") + ") 이 walls 범위 밖이에요.");
                    continue;
                }
                double offset = o.path("offset").asDouble(Double.NaN), width = o.path("width").asDouble(Double.NaN);
                if (Double.isNaN(offset) || Double.isNaN(width) || offset < -EPS || width <= 0) {
                    errors.add(at + " offset/width 가 올바르지 않아요.");
                } else if (offset + width > lengths[wall] + EPS) {
                    errors.add(at + " 이 벽 " + wall + " 길이(" + round(lengths[wall]) + "m) 를 넘어가요 (offset " + offset + " + width " + width + ").");
                }
                double height = o.path("height").asDouble(Double.NaN), sill = o.path("sill").asDouble(Double.NaN);
                if (Double.isNaN(height) || Double.isNaN(sill) || height <= 0 || sill < 0) {
                    errors.add(at + " height/sill 이 올바르지 않아요.");
                } else if (sill + height > fp.path("ceilingHeight").asDouble(0) + EPS) {
                    errors.add(at + " 윗부분(sill+height)이 천장보다 높아요.");
                }
                if (o.has("style") && !java.util.Set.of("swing", "sliding", "open").contains(o.path("style").asText())) {
                    errors.add(at + ".style 은 swing/sliding/open 이어야 해요.");
                }
                JsonNode swing = o.path("swing");
                if (!swing.isMissingNode() && !swing.isNull()) {
                    String hinge = swing.path("hinge").asText();
                    int opensTo = swing.path("opensTo").asInt(0);
                    if ((!hinge.equals("start") && !hinge.equals("end")) || (opensTo != 1 && opensTo != -1)) {
                        errors.add(at + ".swing 은 {hinge: start|end, opensTo: 1|-1} 이어야 해요.");
                    }
                }
            }
        }

        JsonNode rooms = fp.path("rooms");
        if (!rooms.isArray() || rooms.isEmpty()) {
            errors.add("rooms 가 비어 있어요.");
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                JsonNode r = rooms.get(i);
                JsonNode poly = r.path("polygon");
                if (!r.path("name").isTextual() || r.path("name").asText().isBlank()) {
                    errors.add("rooms[" + i + "].name 이 비어 있어요.");
                }
                if (!poly.isArray() || poly.size() < 3) {
                    errors.add("rooms[" + i + "].polygon 은 꼭짓점이 3개 이상이어야 해요.");
                } else {
                    for (int k = 0; k < poly.size(); k++) {
                        if (!point(poly.get(k))) {
                            errors.add("rooms[" + i + "].polygon[" + k + "] 은 [x,y] 숫자 쌍이어야 해요.");
                        }
                    }
                }
                JsonNode floor = r.path("floor");
                if (!floor.isMissingNode() && !floor.isNull()
                        && !java.util.Set.of("wood", "tile", "vinyl", "concrete").contains(floor.path("material").asText())) {
                    errors.add("rooms[" + i + "].floor.material 은 wood/tile/vinyl/concrete 여야 해요.");
                }
            }
        }

        if (!point(fp.path("entrance"))) {
            errors.add("entrance 는 [x,y] 숫자 쌍이어야 해요.");
        }

        JsonNode fixtures = fp.path("fixtures");
        if (!fixtures.isMissingNode() && !fixtures.isNull()) {
            if (!fixtures.isArray()) {
                errors.add("fixtures 가 배열이 아니에요.");
            } else {
                for (int i = 0; i < fixtures.size(); i++) {
                    JsonNode f = fixtures.get(i);
                    if (!f.path("kind").isTextual() || f.path("kind").asText().isBlank()) {
                        errors.add("fixtures[" + i + "].kind 가 비어 있어요.");
                    }
                    if (!point(f.path("position"))) {
                        errors.add("fixtures[" + i + "].position 은 [x,y] 숫자 쌍이어야 해요.");
                    }
                    JsonNode size = f.path("size");
                    if (!size.isMissingNode() && !size.isNull()) {
                        boolean ok = size.isArray() && (size.size() == 2 || size.size() == 3);
                        for (int k = 0; ok && k < size.size(); k++) {
                            ok = positive(size.get(k));
                        }
                        if (!ok) {
                            errors.add("fixtures[" + i + "].size 는 0보다 큰 [폭, 깊이] 또는 [폭, 깊이, 높이] 여야 해요.");
                        }
                    }
                    JsonNode elevation = f.path("elevation");
                    if (!elevation.isMissingNode() && !elevation.isNull()
                            && (!elevation.isNumber() || elevation.asDouble() < 0 || elevation.asDouble() > fp.path("ceilingHeight").asDouble(0))) {
                        errors.add("fixtures[" + i + "].elevation 은 0 이상 천장 높이 이하의 숫자여야 해요.");
                    }
                    if (!FACINGS.contains(f.path("facing").asText())) {
                        errors.add("fixtures[" + i + "].facing 은 N/E/S/W 여야 해요.");
                    }
                }
            }
        }
        return errors;
    }

    private static boolean positive(JsonNode n) {
        return n.isNumber() && n.asDouble() > 0;
    }

    private static boolean point(JsonNode n) {
        return n.isArray() && n.size() == 2 && n.get(0).isNumber() && n.get(1).isNumber();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
