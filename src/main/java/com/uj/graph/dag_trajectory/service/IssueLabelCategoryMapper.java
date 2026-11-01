package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.IssueLabelCategory;
import java.util.Locale;
import java.util.Map;

public final class IssueLabelCategoryMapper {

    private static final Map<String, IssueLabelCategory> CATEGORIES = Map.ofEntries(
            Map.entry("bug", IssueLabelCategory.BUG),
            Map.entry("bugfix", IssueLabelCategory.BUG),
            Map.entry("defect", IssueLabelCategory.BUG),
            Map.entry("fix", IssueLabelCategory.BUG),
            Map.entry("type: bug", IssueLabelCategory.BUG),
            Map.entry("feature", IssueLabelCategory.FEATURE),
            Map.entry("enhancement", IssueLabelCategory.FEATURE),
            Map.entry("type: feature", IssueLabelCategory.FEATURE),
            Map.entry("question", IssueLabelCategory.QUESTION),
            Map.entry("documentation", IssueLabelCategory.DOCUMENTATION),
            Map.entry("docs", IssueLabelCategory.DOCUMENTATION),
            Map.entry("wontfix", IssueLabelCategory.OTHER),
            Map.entry("duplicate", IssueLabelCategory.OTHER),
            Map.entry("invalid", IssueLabelCategory.OTHER),
            Map.entry("other", IssueLabelCategory.OTHER),
            Map.entry("maintenance", IssueLabelCategory.OTHER),
            Map.entry("chore", IssueLabelCategory.OTHER),
            Map.entry("help wanted", IssueLabelCategory.OTHER),
            Map.entry("good first issue", IssueLabelCategory.OTHER));

    private IssueLabelCategoryMapper() {
    }

    public static IssueLabelCategory map(String labelName) {
        if (labelName == null || labelName.isBlank()) {
            return IssueLabelCategory.UNKNOWN;
        }
        return CATEGORIES.getOrDefault(labelName.trim().toLowerCase(Locale.ROOT), IssueLabelCategory.UNKNOWN);
    }
}
