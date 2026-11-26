package com.guvi.recommendationsystem.model;

import com.guvi.recommendationsystem.catalog.IngestionController;
import com.guvi.recommendationsystem.catalog.SourceHttp;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RecordSnapshotTest {
    @Test void pagedResultsHaveNoMutableAliases() {
        var source = new ArrayList<>(List.of("original"));
        var page = new CatalogPage<>(source, 1, 20, 1, 1);
        source.set(0, "changed");
        assertThat(page.items()).containsExactly("original");
        assertThatThrownBy(() -> page.items().add("injected")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void requestsPreserveInvalidValuesForTheValidationLayer() {
        var ids = new ArrayList<Long>(Arrays.asList(1L, null));
        var request = new OnboardingRequest(OnboardingPurpose.LEARN, 1, ids);
        ids.clear();
        assertThat(request.interestIds()).containsExactly(1L, null);
        assertThatThrownBy(() -> request.interestIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new OnboardingRequest(null, 0, null).interestIds()).isNull();
        assertThat(new IngestionController.ImportRequest(null, null).resources()).isNull();
    }

    @Test void sourceBodiesAreCopiedInBothDirections() {
        byte[] original = {1, 2, 3};
        var response = new SourceHttp.Result(200, URI.create("https://openstax.org/"), original);
        original[0] = 9;
        response.body()[1] = 9;
        assertThat(response.body()).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test void csvPreviewErrorsCannotBeRewrittenAfterValidation() {
        var errors = new ArrayList<>(List.of("Invalid URL"));
        var row = new CatalogCsvPreview.Row(2, "Example", "Technology", false, errors);
        errors.clear();
        assertThat(row.errors()).containsExactly("Invalid URL");
        assertThatThrownBy(() -> row.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
