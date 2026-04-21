package com.gavel.auction.closer;

import com.gavel.auction.service.AuctionTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateSchedulerTest {

    @Mock private AuctionTemplateService templateService;
    private TemplateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TemplateScheduler(templateService);
    }

    @Test
    void checkTemplates_delegatesToService() {
        scheduler.checkTemplates();

        verify(templateService).processScheduledTemplates();
    }

    @Test
    void checkTemplates_handlesExceptionGracefully() {
        doThrow(new RuntimeException("db down")).when(templateService).processScheduledTemplates();

        scheduler.checkTemplates();

        verify(templateService).processScheduledTemplates();
    }
}
