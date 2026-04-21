package com.gavel.auction.service;

import com.gavel.auction.model.AuctionTemplate;
import com.gavel.auction.model.CreateAuctionRequest;
import com.gavel.auction.model.CreateTemplateRequest;
import com.gavel.auction.repository.AuctionTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AuctionTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AuctionTemplateService.class);

    private final AuctionTemplateRepository templateRepo;
    private final AuctionService auctionService;

    public AuctionTemplateService(AuctionTemplateRepository templateRepo,
                                  AuctionService auctionService) {
        this.templateRepo = templateRepo;
        this.auctionService = auctionService;
    }

    public AuctionTemplate createTemplate(CreateTemplateRequest req, String sellerId) {
        if (req.itemId() == null || req.itemId().isBlank()) {
            throw new AuctionService.ValidationException("item_id is required");
        }
        if (req.shopId() == null || req.shopId().isBlank()) {
            throw new AuctionService.ValidationException("shop_id is required");
        }
        if (req.durationMinutes() < 1 || req.durationMinutes() > 10080) {
            throw new AuctionService.ValidationException("duration_minutes must be between 1 and 10080");
        }
        if (req.startBid() < 0) {
            throw new AuctionService.ValidationException("start_bid must be non-negative");
        }
        if (req.scheduleType() == null || req.scheduleType().isBlank()) {
            throw new AuctionService.ValidationException("schedule_type is required (daily or weekly)");
        }
        if (!"daily".equals(req.scheduleType()) && !"weekly".equals(req.scheduleType())) {
            throw new AuctionService.ValidationException("schedule_type must be daily or weekly");
        }
        if (req.scheduleTime() == null || req.scheduleTime().isBlank()) {
            throw new AuctionService.ValidationException("schedule_time is required (HH:mm)");
        }
        if ("weekly".equals(req.scheduleType()) && (req.scheduleDays() == null || req.scheduleDays().isBlank())) {
            throw new AuctionService.ValidationException("schedule_days is required for weekly schedules (e.g. MON,WED,FRI)");
        }
        if (req.pickupOffsetMinutes() <= 0) {
            throw new AuctionService.ValidationException("pickup_offset_minutes must be positive");
        }
        if (req.pickupWindowMinutes() <= 0) {
            throw new AuctionService.ValidationException("pickup_window_minutes must be positive");
        }

        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId(UUID.randomUUID().toString());
        template.setSellerId(sellerId);
        template.setShopId(req.shopId());
        template.setItemId(req.itemId());
        template.setItemTitle(req.itemTitle() != null ? req.itemTitle() : "");
        template.setShopName(req.shopName() != null ? req.shopName() : "");
        template.setShopLat(req.shopLat());
        template.setShopLng(req.shopLng());
        template.setRetailPrice(req.retailPrice());
        template.setMaxPrice(req.maxPrice());
        template.setMinIncrement(req.minIncrement());
        template.setQuantity(req.quantity() > 0 ? req.quantity() : 1);
        template.setImageUrl(req.imageUrl() != null ? req.imageUrl() : "");
        template.setShopLogoUrl(req.shopLogoUrl() != null ? req.shopLogoUrl() : "");
        template.setDescription(req.description() != null ? req.description() : "");
        template.setCategory(req.category() != null ? req.category() : "");
        template.setDurationMinutes(req.durationMinutes());
        template.setStartBid(req.startBid());
        template.setPickupOffsetMinutes(req.pickupOffsetMinutes());
        template.setPickupWindowMinutes(req.pickupWindowMinutes());
        template.setScheduleType(req.scheduleType());
        template.setScheduleDays(req.scheduleDays() != null ? req.scheduleDays().toUpperCase() : "");
        template.setScheduleTime(req.scheduleTime());
        template.setActive(true);
        template.setCreatedAt(Instant.now().toString());

        String nextRun = computeNextRun(template);
        template.setNextRunAt(nextRun);

        templateRepo.save(template);
        return template;
    }

    public AuctionTemplate getTemplate(String templateId) {
        AuctionTemplate t = templateRepo.getById(templateId);
        if (t == null) throw new AuctionService.NotFoundException("template not found");
        return t;
    }

    public List<AuctionTemplate> listByShop(String shopId) {
        return templateRepo.listByShop(shopId);
    }

    public void toggleActive(String templateId, String sellerId, boolean active) {
        AuctionTemplate t = templateRepo.getById(templateId);
        if (t == null) throw new AuctionService.NotFoundException("template not found");
        if (!t.getSellerId().equals(sellerId)) {
            throw new AuctionService.ForbiddenException("not your template");
        }
        templateRepo.setActive(templateId, active);
        if (active) {
            t.setActive(true);
            String nextRun = computeNextRun(t);
            templateRepo.updateNextRunAt(templateId, nextRun);
        }
    }

    public void deleteTemplate(String templateId, String sellerId) {
        AuctionTemplate t = templateRepo.getById(templateId);
        if (t == null) throw new AuctionService.NotFoundException("template not found");
        if (!t.getSellerId().equals(sellerId)) {
            throw new AuctionService.ForbiddenException("not your template");
        }
        templateRepo.delete(templateId);
    }

    public void processScheduledTemplates() {
        List<AuctionTemplate> active = templateRepo.listActive();
        Instant now = Instant.now();

        for (AuctionTemplate t : active) {
            try {
                if (t.getNextRunAt() == null || t.getNextRunAt().isBlank()) continue;
                Instant nextRun = Instant.parse(t.getNextRunAt());
                if (!nextRun.isAfter(now)) {
                    generateAuctionFromTemplate(t);
                    String newNextRun = computeNextRun(t);
                    templateRepo.updateNextRunAt(t.getTemplateId(), newNextRun);
                    log.info("Generated auction from template {} ({}), next run: {}",
                            t.getTemplateId(), t.getItemTitle(), newNextRun);
                }
            } catch (Exception e) {
                log.error("Failed to process template {}: {}", t.getTemplateId(), e.getMessage());
            }
        }
    }

    private void generateAuctionFromTemplate(AuctionTemplate t) {
        Instant auctionStart = Instant.now();
        Instant auctionEnd = auctionStart.plus(Duration.ofMinutes(t.getDurationMinutes()));
        Instant pickupStart = auctionEnd.plus(Duration.ofMinutes(t.getPickupOffsetMinutes()));
        Instant pickupEnd = pickupStart.plus(Duration.ofMinutes(t.getPickupWindowMinutes()));

        CreateAuctionRequest req = new CreateAuctionRequest(
                t.getItemId(), t.getItemTitle(),
                t.getShopId(), t.getShopName(),
                t.getShopLat(), t.getShopLng(),
                t.getRetailPrice(), t.getMaxPrice(), t.getMinIncrement(),
                t.getQuantity(),
                t.getImageUrl(), t.getShopLogoUrl(),
                t.getDescription(), t.getCategory(),
                t.getDurationMinutes(), t.getStartBid(),
                null,
                pickupStart.toString(), pickupEnd.toString(),
                0
        );

        auctionService.createAuction(req, t.getSellerId());
    }

    String computeNextRun(AuctionTemplate t) {
        LocalTime time = LocalTime.parse(t.getScheduleTime(), DateTimeFormatter.ofPattern("HH:mm"));
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime candidate = now.toLocalDate().atTime(time).atZone(ZoneOffset.UTC);

        if (!candidate.toInstant().isAfter(Instant.now())) {
            candidate = candidate.plusDays(1);
        }

        if ("weekly".equals(t.getScheduleType())) {
            Set<DayOfWeek> days = parseDays(t.getScheduleDays());
            if (days.isEmpty()) {
                return candidate.toInstant().toString();
            }
            for (int i = 0; i < 8; i++) {
                if (days.contains(candidate.getDayOfWeek())) {
                    return candidate.toInstant().toString();
                }
                candidate = candidate.plusDays(1);
            }
        }

        return candidate.toInstant().toString();
    }

    private Set<DayOfWeek> parseDays(String scheduleDays) {
        if (scheduleDays == null || scheduleDays.isBlank()) return Set.of();
        Set<DayOfWeek> days = new HashSet<>();
        for (String d : scheduleDays.split(",")) {
            try {
                days.add(toDayOfWeek(d.trim()));
            } catch (Exception ignored) {}
        }
        return days;
    }

    private DayOfWeek toDayOfWeek(String abbrev) {
        return switch (abbrev.toUpperCase()) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown day: " + abbrev);
        };
    }
}
