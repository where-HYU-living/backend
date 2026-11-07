package com.chatatda.api.geo;

import java.util.List;

public record WalkResult(int minutes, int distanceM, List<double[]> path) {
}
