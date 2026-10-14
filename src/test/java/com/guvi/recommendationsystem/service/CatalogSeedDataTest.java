package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.model.ItemDifficulty;
import com.guvi.recommendationsystem.model.ItemType;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedDataTest {
    @Test
    void curatedCatalogHasBalancedNichesValidMetadataAndUniquePublicUrls() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (var stream = getClass().getClassLoader().getResourceAsStream("catalog/curated-resources.tsv")) {
            assertThat(stream).isNotNull();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) rows.add(line.split("\\t", -1));
                }
            }
        }

        assertThat(rows).hasSize(175);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).hasSize(9);
            assertThat(row[2]).startsWith("https://");
            assertThat(row[1]).isNotBlank();
            assertThat(row[3]).isNotBlank();
            assertThat(ItemType.valueOf(row[5])).isNotNull();
            assertThat(ItemDifficulty.valueOf(row[6])).isNotNull();
            assertThat(Integer.parseInt(row[7])).isBetween(1, 1440);
            assertThat(row[4].split("\\|")).hasSizeBetween(3, 6);
            assertThat(row[8]).isIn("true", "false");
        });
        assertThat(rows.stream().map(row -> row[2]).collect(Collectors.toSet())).hasSize(175);
        assertThat(rows.stream().map(row -> row[5]).collect(Collectors.toSet())).hasSizeGreaterThanOrEqualTo(4);
        assertThat(rows.stream().map(row -> row[6]).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("BEGINNER", "INTERMEDIATE", "ADVANCED");
        assertThat(rows.stream().filter(row -> Boolean.parseBoolean(row[8]))).hasSize(25);
        Map<String, Long> nicheCounts = rows.stream().collect(Collectors.groupingBy(row -> row[0], Collectors.counting()));
        assertThat(nicheCounts).containsEntry("Technology", 34L)
                .containsEntry("Business & Finance", 35L)
                .containsEntry("Learning & Career", 35L)
                .containsEntry("Health & Fitness", 35L)
                .containsEntry("Creative & Lifestyle", 36L);
        Map<String, Long> editorPickCounts = rows.stream().filter(row -> Boolean.parseBoolean(row[8]))
                .collect(Collectors.groupingBy(row -> row[0], Collectors.counting()));
        assertThat(editorPickCounts.values()).allSatisfy(count -> assertThat(count).isEqualTo(5L));
        assertThat(new HashSet<>(nicheCounts.keySet())).hasSize(5);
    }
}
