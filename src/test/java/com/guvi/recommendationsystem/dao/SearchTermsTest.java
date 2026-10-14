package com.guvi.recommendationsystem.dao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTermsTest {
    @Test
    void splitsPhrasesAndDropsGenericCourseWords() {
        assertThat(SearchTerms.tokenize("  Cloud computing courses  ")).containsExactly("cloud", "computing");
    }

    @Test
    void removesDuplicateTermsAndBoundsExpansion() {
        assertThat(SearchTerms.tokenize("java Java java spring boot backend security testing database"))
                .containsExactly("java", "spring", "boot", "backend", "security", "testing", "database");
    }

    @Test
    void safelyEscapesSqlLikeWildcards() {
        assertThat(SearchTerms.likePattern("100%_safe!" )).isEqualTo("%100!%!_safe!!%");
    }
}
