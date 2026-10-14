package com.guvi.recommendationsystem.model;

import java.util.List;

public record CatalogCsvPreview(int validRows, int invalidRows, boolean canImport, List<Row> rows) {
    public record Row(int line, String title, String category, boolean valid, List<String> errors) { }
}
