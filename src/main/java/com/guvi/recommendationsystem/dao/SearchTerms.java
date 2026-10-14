package com.guvi.recommendationsystem.dao;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SearchTerms {
    private static final Set<String> STOP_WORDS = Set.of("a", "about", "an", "and", "are", "course", "courses",
            "content", "find", "for", "from", "in", "learn", "learning", "of", "on", "or", "resource",
            "resources", "the", "to", "with");

    private SearchTerms() { }

    static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) return List.of();
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() > 1 && !STOP_WORDS.contains(term))
                .distinct().limit(8).toList();
    }

    static String likePattern(String term) {
        return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
