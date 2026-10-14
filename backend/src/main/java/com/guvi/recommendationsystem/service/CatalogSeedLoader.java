package com.guvi.recommendationsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.guvi.recommendationsystem.dao.ItemDao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Adds curated public learning resources once; subsequent starts skip URLs already in the catalog. */
@Component
public class CatalogSeedLoader implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(CatalogSeedLoader.class);
    private final CatalogCsvService catalogCsvService;
    private final JdbcTemplate jdbcTemplate;
    private final ItemDao itemDao;

    public CatalogSeedLoader(CatalogCsvService catalogCsvService, JdbcTemplate jdbcTemplate, ItemDao itemDao) {
        this.catalogCsvService = catalogCsvService;
        this.jdbcTemplate = jdbcTemplate;
        this.itemDao = itemDao;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/curated-resources.tsv");
        if (!resource.exists()) return;
        StringBuilder csv = new StringBuilder(
                "category,title,description,type,url,source,difficulty,estimatedMinutes,tags\n");
        List<String> newEditorPickUrls = new java.util.ArrayList<>();
        int rows = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank()) continue;
                List<String> fields = List.of(line.split("\\t", -1));
                if (fields.size() != 9) throw new IllegalStateException("Invalid curated catalog seed row " + (rows + 2));
                String title = fields.get(1);
                String source = fields.get(3);
                String tags = fields.get(4);
                if (Boolean.parseBoolean(fields.get(8)) && !itemDao.existsByExternalUrl(fields.get(2))) {
                    newEditorPickUrls.add(fields.get(2));
                }
                String description = "Explore " + title + ", a public learning resource from " + source
                        + ". Use its linked material to build practical familiarity with "
                        + tags.replace('|', ',') + " and choose a useful next step.";
                csv.append(csv(fields.get(0))).append(',').append(csv(title)).append(',')
                        .append(csv(description)).append(',').append(csv(fields.get(5))).append(',')
                        .append(csv(fields.get(2))).append(',').append(csv(source)).append(',')
                        .append(csv(fields.get(6))).append(',').append(csv(fields.get(7))).append(',')
                        .append(csv(tags)).append('\n');
                rows++;
            }
        }
        if (rows == 0) return;
        var result = catalogCsvService.commit(csv.toString());
        for (String url : newEditorPickUrls) {
            jdbcTemplate.update("UPDATE items SET is_editor_pick = TRUE WHERE external_url = ?", url);
        }
        logger.info("Curated catalog seed: imported {}, already present {}", result.imported(), result.skippedExisting());
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
