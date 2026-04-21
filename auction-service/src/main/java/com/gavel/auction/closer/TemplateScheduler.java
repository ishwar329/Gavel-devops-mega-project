package com.gavel.auction.closer;

import com.gavel.auction.service.AuctionTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TemplateScheduler {

    private static final Logger log = LoggerFactory.getLogger(TemplateScheduler.class);

    private final AuctionTemplateService templateService;

    public TemplateScheduler(AuctionTemplateService templateService) {
        this.templateService = templateService;
    }

    @Scheduled(fixedRate = 30000)
    public void checkTemplates() {
        try {
            templateService.processScheduledTemplates();
        } catch (Exception e) {
            log.error("Error processing auction templates: {}", e.getMessage());
        }
    }
}
