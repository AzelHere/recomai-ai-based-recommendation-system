package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.ItemInterestDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.CatalogCsvImportResult;
import com.guvi.recommendationsystem.model.CatalogCsvPreview;
import com.guvi.recommendationsystem.model.Category;
import com.guvi.recommendationsystem.model.Interest;
import com.guvi.recommendationsystem.model.ItemDifficulty;
import com.guvi.recommendationsystem.model.ItemRequest;
import com.guvi.recommendationsystem.model.ItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogCsvService {
    private static final int MAX_CHARS = 1_000_000;
    private static final int MAX_ROWS = 500;
    private static final List<String> HEADERS = List.of(
            "category", "title", "description", "type", "url", "source", "difficulty", "estimatedMinutes", "tags");

    private final CategoryDao categoryDao;
    private final InterestDao interestDao;
    private final ItemDao itemDao;
    private final ItemInterestDao itemInterestDao;
    private final ItemService itemService;

    public CatalogCsvService(CategoryDao categoryDao, InterestDao interestDao, ItemDao itemDao,
                             ItemInterestDao itemInterestDao, ItemService itemService) {
        this.categoryDao = categoryDao;
        this.interestDao = interestDao;
        this.itemDao = itemDao;
        this.itemInterestDao = itemInterestDao;
        this.itemService = itemService;
    }

    public CatalogCsvPreview preview(String csv) {
        List<ParsedRow> parsed = parse(csv);
        List<Category> categories = categoryDao.findActive();
        Map<String, Category> categoriesByName = new LinkedHashMap<>();
        categories.forEach(category -> categoriesByName.put(normalize(category.name()), category));
        Map<String, Interest> interestsByName = new LinkedHashMap<>();
        interestDao.findActive().forEach(interest -> interestsByName.put(normalize(interest.name()), interest));

        List<CatalogCsvPreview.Row> rows = new ArrayList<>();
        int valid = 0;
        for (ParsedRow row : parsed) {
            List<String> errors = validate(row, categoriesByName, interestsByName);
            boolean rowValid = errors.isEmpty();
            if (rowValid) valid++;
            String title = row.values().size() > 1 ? row.values().get(1) : "";
            String category = row.values().isEmpty() ? "" : row.values().getFirst();
            rows.add(new CatalogCsvPreview.Row(row.line(), title, category, rowValid, errors));
        }
        return new CatalogCsvPreview(valid, rows.size() - valid, valid == rows.size(), rows);
    }

    @Transactional
    public CatalogCsvImportResult commit(String csv) {
        CatalogCsvPreview preview = preview(csv);
        if (!preview.canImport()) {
            throw new InvalidUserInputException("Fix every invalid CSV row before importing.");
        }
        List<ParsedRow> parsed = parse(csv);
        Map<String, Category> categories = new LinkedHashMap<>();
        categoryDao.findActive().forEach(category -> categories.put(normalize(category.name()), category));
        Map<String, Interest> interests = new LinkedHashMap<>();
        interestDao.findActive().forEach(interest -> interests.put(normalize(interest.name()), interest));
        int imported = 0;
        int skipped = 0;
        Set<String> seenUrls = new HashSet<>();
        for (ParsedRow row : parsed) {
            String url = row.values().get(4).trim();
            String key = normalize(url);
            if (!seenUrls.add(key) || itemDao.existsByExternalUrl(url)) {
                skipped++;
                continue;
            }
            Category category = categories.get(normalize(row.values().get(0)));
            String[] values = row.values().toArray(String[]::new);
            ItemType type = ItemType.valueOf(values[3].trim().toUpperCase(Locale.ROOT));
            ItemDifficulty difficulty = ItemDifficulty.valueOf(values[6].trim().toUpperCase(Locale.ROOT));
            Integer minutes = Integer.valueOf(values[7].trim());
            var item = itemService.create(new ItemRequest(category.categoryId(), values[1].trim(), values[2].trim(),
                    type, url, null, values[5].trim(), difficulty, minutes, new BigDecimal("0.7500")));
            Arrays.stream(values[8].split("\\|"))
                    .map(String::trim).filter(tag -> !tag.isBlank())
                    .map(tag -> interests.get(normalize(tag)))
                    .forEach(tag -> itemInterestDao.save(item.itemId(), tag.interestId(), BigDecimal.ONE));
            imported++;
        }
        return new CatalogCsvImportResult(imported, skipped);
    }

    private List<String> validate(ParsedRow row, Map<String, Category> categories, Map<String, Interest> interests) {
        List<String> errors = new ArrayList<>();
        List<String> v = row.values();
        if (v.size() != HEADERS.size()) return List.of("Expected 9 columns.");
        if (!categories.containsKey(normalize(v.get(0)))) errors.add("Category must match an active category name.");
        required(v.get(1), "Title", 150, errors);
        required(v.get(2), "Description", 60000, errors);
        if (v.get(2).trim().split("(?<=[.!?])\\s+").length < 2) errors.add("Description must contain at least two sentences.");
        try { ItemType.valueOf(v.get(3).trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { errors.add("Type is not supported."); }
        try {
            URI uri = URI.create(v.get(4).trim());
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                errors.add("URL must be an absolute HTTP or HTTPS address.");
        } catch (RuntimeException ex) { errors.add("URL is not valid."); }
        required(v.get(5), "Source", 160, errors);
        try { ItemDifficulty.valueOf(v.get(6).trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { errors.add("Difficulty is not supported."); }
        try { int minutes = Integer.parseInt(v.get(7).trim()); if (minutes < 1 || minutes > 1440) errors.add("Estimated minutes must be from 1 to 1440."); }
        catch (RuntimeException ex) { errors.add("Estimated minutes must be a whole number."); }
        List<String> tags = Arrays.stream(v.get(8).split("\\|" )).map(String::trim).filter(tag -> !tag.isEmpty()).toList();
        if (tags.size() < 3 || tags.size() > 6) errors.add("Provide 3 to 6 topic tags separated by |.");
        for (String tag : tags) if (!interests.containsKey(normalize(tag))) errors.add("Unknown topic tag: " + tag + ".");
        return List.copyOf(errors);
    }

    private List<ParsedRow> parse(String csv) {
        if (csv == null || csv.isBlank() || csv.length() > MAX_CHARS) throw new InvalidUserInputException("CSV is required and must be at most 1 MB.");
        List<List<String>> records = parseRecords(csv);
        if (records.size() < 2 || records.size() - 1 > MAX_ROWS) throw new InvalidUserInputException("CSV must contain 1 to 500 data rows.");
        List<String> headers = records.getFirst().stream().map(String::trim).toList();
        if (!headers.equals(HEADERS)) throw new InvalidUserInputException("CSV headers must be: " + String.join(",", HEADERS) + ".");
        List<ParsedRow> result = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) result.add(new ParsedRow(i + 1, records.get(i)));
        return result;
    }

    private List<List<String>> parseRecords(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else cell.append(ch);
            } else if (ch == '"' && cell.isEmpty()) quoted = true;
            else if (ch == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isBlank())) records.add(List.copyOf(row));
                row.clear();
            } else cell.append(ch);
        }
        if (quoted) throw new InvalidUserInputException("CSV contains an unterminated quoted field.");
        if (!row.isEmpty() || !cell.isEmpty()) { row.add(cell.toString()); records.add(List.copyOf(row)); }
        return records;
    }

    private void required(String value, String label, int max, List<String> errors) {
        if (value == null || value.isBlank()) errors.add(label + " is required.");
        else if (value.trim().length() > max) errors.add(label + " must be at most " + max + " characters.");
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private record ParsedRow(int line, List<String> values) { }
}
