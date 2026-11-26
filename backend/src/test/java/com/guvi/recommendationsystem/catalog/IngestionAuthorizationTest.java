package com.guvi.recommendationsystem.catalog;

import com.guvi.recommendationsystem.web.SessionAccess;
import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IngestionAuthorizationTest {
    @Test void authorizationRunsBeforePreviewCommitOrMaintenance(){
        var service=mock(CatalogIngestionService.class);var maintenance=mock(CatalogMaintenance.class);var sessions=mock(SessionAccess.class);var request=mock(HttpServletRequest.class);
        when(sessions.requireAdmin(request)).thenThrow(new ForbiddenRequestException("Administrator access required"));
        var controller=new IngestionController(service,maintenance,sessions);
        assertThatThrownBy(()->controller.preview(new IngestionController.ImportRequest(null,null),request)).isInstanceOf(ForbiddenRequestException.class);
        assertThatThrownBy(()->controller.commit(new IngestionController.ImportRequest(null,null),request)).isInstanceOf(ForbiddenRequestException.class);
        assertThatThrownBy(()->controller.check(request)).isInstanceOf(ForbiddenRequestException.class);
        verifyNoInteractions(service,maintenance);
    }
}
