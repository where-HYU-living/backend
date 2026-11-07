package com.chatatda.api.floorplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class FloorPlanValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID = """
            { "id": 1, "name": "t", "areaM2": 10, "ceilingHeight": 2.4,
              "walls": [ { "from": [0, 0], "to": [3, 0], "thickness": 0.2 }, { "from": [3, 0], "to": [3, 3], "thickness": 0.2 } ],
              "openings": [ { "type": "door", "wall": 0, "offset": 1, "width": 0.9, "height": 2.1, "sill": 0, "style": "swing", "swing": { "hinge": "start", "opensTo": 1 } } ],
              "rooms": [ { "name": "욕실", "kind": "bathroom", "polygon": [[0, 0], [3, 0], [3, 3]], "floor": { "material": "tile", "height": -0.08 } } ],
              "entrance": [1.4, 0],
              "fixtures": [ { "kind": "toilet", "position": [2, 1], "facing": "W", "room": "욕실" } ],
              "glbUrl": null }""";

    private static List<String> check(String json) throws Exception {
        return FloorPlanValidator.validate(MAPPER.readTree(json));
    }

    @Test
    void valid_planWithNewFields_passes() throws Exception {
        assertThat(check(VALID)).isEmpty();
    }

    @Test
    void optionalFieldsMayBeMissing() throws Exception {
        JsonNode fp = MAPPER.readTree(VALID);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fp).remove("fixtures");
        ((com.fasterxml.jackson.databind.node.ObjectNode) fp.get("openings").get(0)).remove(List.of("style", "swing"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) fp.get("rooms").get(0)).remove(List.of("kind", "floor"));
        assertThat(FloorPlanValidator.validate(fp)).isEmpty();
    }

    @Test
    void openingOnMissingWall_isReported() throws Exception {
        assertThat(check(VALID.replace("\"wall\": 0", "\"wall\": 5"))).anyMatch(m -> m.contains("openings[0].wall") && m.contains("범위 밖"));
    }

    @Test
    void openingLongerThanWall_isReported() throws Exception {
        assertThat(check(VALID.replace("\"offset\": 1,", "\"offset\": 2.5,"))).anyMatch(m -> m.contains("길이") && m.contains("넘어가"));
    }

    @Test
    void openingTallerThanCeiling_isReported() throws Exception {
        assertThat(check(VALID.replace("\"height\": 2.1", "\"height\": 3.0"))).anyMatch(m -> m.contains("천장"));
    }

    @Test
    void roomWithTwoPoints_isReported() throws Exception {
        assertThat(check(VALID.replace("[[0, 0], [3, 0], [3, 3]]", "[[0, 0], [3, 0]]"))).anyMatch(m -> m.contains("polygon"));
    }

    @Test
    void badEnumsAndFacing_areReported() throws Exception {
        assertThat(check(VALID.replace("\"tile\"", "\"marble\"").replace("\"W\"", "\"X\"").replace("\"swing\",", "\"flip\",")))
                .anyMatch(m -> m.contains("floor.material"))
                .anyMatch(m -> m.contains("facing"))
                .anyMatch(m -> m.contains("style"));
    }

    @Test
    void badSwing_isReported() throws Exception {
        assertThat(check(VALID.replace("\"opensTo\": 1", "\"opensTo\": 2"))).anyMatch(m -> m.contains("swing"));
    }

    @Test
    void zeroLengthWallAndMissingFields_areReported() throws Exception {
        assertThat(check("{ \"id\": 1, \"walls\": [ { \"from\": [1, 1], \"to\": [1, 1], \"thickness\": 0.2 } ] }"))
                .anyMatch(m -> m.contains("길이가 0"))
                .anyMatch(m -> m.contains("name"))
                .anyMatch(m -> m.contains("rooms"))
                .anyMatch(m -> m.contains("entrance"));
    }

    @Test
    void fixtureSize_acceptsTwoOrThreeNumbers_rejectsOthers() throws Exception {
        String withSize = VALID.replace("\"facing\": \"W\"", "\"facing\": \"W\", \"size\": %s");
        assertThat(check(withSize.formatted("[0.5, 0.6]"))).isEmpty();
        assertThat(check(withSize.formatted("[0.5, 0.6, 0.8]"))).isEmpty();
        assertThat(check(withSize.formatted("[0.5]"))).anyMatch(m -> m.contains("size"));
        assertThat(check(withSize.formatted("[0.5, 0, 0.8]"))).anyMatch(m -> m.contains("size"));
    }

    @Test
    void notAnObject_isReported() throws Exception {
        assertThat(check("[1]")).hasSize(1);
    }
}
