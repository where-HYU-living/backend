package com.chatatda.api.catalog;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class CatalogApiTest extends IntegrationTest {

    @Test
    void departments_listed() throws Exception {
        mvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].name").value("테스트 컴퓨터학과"))
                .andExpect(jsonPath("$[0].college").value("테스트 공과대학"))
                .andExpect(jsonPath("$[0].defaultBuildingIds").isArray())
                .andExpect(jsonPath("$[0].defaultBuildingIds[0]").value(101))
                .andExpect(jsonPath("$[0].defaultBuildingIds[1]").value(102));
    }

    @Test
    void buildings_filterByType() throws Exception {
        mvc.perform(get("/api/v1/buildings").param("type", "GATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].type", everyItem(is("GATE"))));
    }

    @Test
    void buildings_allWhenTypeOmitted() throws Exception {
        mvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].lat").value(37.5575))
                .andExpect(jsonPath("$[0].lng").value(127.045));
    }

    @Test
    void buildings_invalidType_400() throws Exception {
        mvc.perform(get("/api/v1/buildings").param("type", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
