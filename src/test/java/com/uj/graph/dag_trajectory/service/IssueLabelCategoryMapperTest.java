package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.IssueLabelCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueLabelCategoryMapperTest {

    @Test
    void mapsKnownLabelsWithoutChangingTheirStoredNames() {
        assertEquals(IssueLabelCategory.BUG, IssueLabelCategoryMapper.map("Bug"));
        assertEquals(IssueLabelCategory.FEATURE, IssueLabelCategoryMapper.map("enhancement"));
        assertEquals(IssueLabelCategory.QUESTION, IssueLabelCategoryMapper.map("question"));
        assertEquals(IssueLabelCategory.DOCUMENTATION, IssueLabelCategoryMapper.map("docs"));
        assertEquals(IssueLabelCategory.OTHER, IssueLabelCategoryMapper.map("good first issue"));
    }

    @Test
    void mapsUnrecognizedOrMissingLabelsToUnknown() {
        assertEquals(IssueLabelCategory.UNKNOWN, IssueLabelCategoryMapper.map("needs-triage"));
        assertEquals(IssueLabelCategory.UNKNOWN, IssueLabelCategoryMapper.map(null));
    }
}
