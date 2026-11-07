package com.chatatda.api.realdeal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DongAdjacencyTest {

    private final DongAdjacency adjacency = new DongAdjacency();

    @Test
    void neighbors_areSymmetric() {
        for (String dong : adjacency.dongs()) {
            for (String neighbor : adjacency.neighbors(dong)) {
                assertThat(adjacency.neighbors(neighbor)).as(neighbor + " → " + dong).contains(dong);
            }
        }
    }

    @Test
    void neighbors_ofHaengdang() {
        assertThat(adjacency.neighbors("행당동")).contains("사근동", "마장동").doesNotContain("행당동");
    }

    @Test
    void unknownDong_hasNoNeighbors() {
        assertThat(adjacency.neighbors("없는동")).isEmpty();
    }
}
