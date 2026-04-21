package com.gavel.auction.service;

import com.gavel.auction.model.AuctionTemplate;
import com.gavel.auction.model.CreateTemplateRequest;
import com.gavel.auction.repository.AuctionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionTemplateServiceTest {

    @Mock private AuctionTemplateRepository templateRepo;
    @Mock private AuctionService auctionService;

    private AuctionTemplateService service;

    @BeforeEach
    void setUp() {
        service = new AuctionTemplateService(templateRepo, auctionService);
    }

    private CreateTemplateRequest validDailyRequest() {
        return new CreateTemplateRequest(
                "item-1", "Fresh Bread", "shop-1", "Green Bakery",
                40.7128, -74.0060, 1000, 5000, 100, 1,
                "http://img.com/1.jpg", "http://img.com/logo.jpg",
                "Daily fresh bread", "Bakery",
                60, 500, 30, 60,
                "daily", null, "08:00"
        );
    }

    private CreateTemplateRequest validWeeklyRequest() {
        return new CreateTemplateRequest(
                "item-1", "Fresh Bread", "shop-1", "Green Bakery",
                40.7128, -74.0060, 1000, 5000, 100, 1,
                "http://img.com/1.jpg", "http://img.com/logo.jpg",
                "Weekly fresh bread", "Bakery",
                60, 500, 30, 60,
                "weekly", "MON,WED,FRI", "08:00"
        );
    }

    @Test
    void createTemplate_validDaily_createsAndPersists() {
        AuctionTemplate result = service.createTemplate(validDailyRequest(), "seller-1");

        assertNotNull(result.getTemplateId());
        assertEquals("seller-1", result.getSellerId());
        assertEquals("shop-1", result.getShopId());
        assertEquals("item-1", result.getItemId());
        assertEquals("daily", result.getScheduleType());
        assertEquals("08:00", result.getScheduleTime());
        assertTrue(result.isActive());
        assertNotNull(result.getNextRunAt());
        verify(templateRepo).save(any(AuctionTemplate.class));
    }

    @Test
    void createTemplate_validWeekly_setsScheduleDays() {
        AuctionTemplate result = service.createTemplate(validWeeklyRequest(), "seller-1");

        assertEquals("weekly", result.getScheduleType());
        assertEquals("MON,WED,FRI", result.getScheduleDays());
        verify(templateRepo).save(any(AuctionTemplate.class));
    }

    @Test
    void createTemplate_missingItemId_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("item_id is required", ex.getMessage());
    }

    @Test
    void createTemplate_missingShopId_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("shop_id is required", ex.getMessage());
    }

    @Test
    void createTemplate_invalidDuration_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 0, 0, 30, 60,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("duration_minutes must be between 1 and 10080", ex.getMessage());
    }

    @Test
    void createTemplate_negativeStartBid_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, -1, 30, 60,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("start_bid must be non-negative", ex.getMessage());
    }

    @Test
    void createTemplate_invalidScheduleType_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "monthly", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("schedule_type must be daily or weekly", ex.getMessage());
    }

    @Test
    void createTemplate_missingScheduleType_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                null, null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("schedule_type is required (daily or weekly)", ex.getMessage());
    }

    @Test
    void createTemplate_missingScheduleTime_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "daily", null, null
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("schedule_time is required (HH:mm)", ex.getMessage());
    }

    @Test
    void createTemplate_weeklyWithoutDays_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "weekly", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("schedule_days is required for weekly schedules (e.g. MON,WED,FRI)", ex.getMessage());
    }

    @Test
    void createTemplate_zeroPickupOffset_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 0, 60,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("pickup_offset_minutes must be positive", ex.getMessage());
    }

    @Test
    void createTemplate_zeroPickupWindow_throws() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 0,
                "daily", null, "08:00"
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createTemplate(req, "seller-1"));
        assertEquals("pickup_window_minutes must be positive", ex.getMessage());
    }

    @Test
    void createTemplate_quantityZero_defaultsToOne() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 0,
                null, null, null, null, 60, 0, 30, 60,
                "daily", null, "08:00"
        );

        AuctionTemplate result = service.createTemplate(req, "seller-1");
        assertEquals(1, result.getQuantity());
    }

    @Test
    void getTemplate_exists_returnsTemplate() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        when(templateRepo.getById("t-1")).thenReturn(template);

        AuctionTemplate result = service.getTemplate("t-1");
        assertEquals("t-1", result.getTemplateId());
    }

    @Test
    void getTemplate_notFound_throws() {
        when(templateRepo.getById("t-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.getTemplate("t-1"));
    }

    @Test
    void listByShop_delegatesToRepo() {
        List<AuctionTemplate> expected = List.of(new AuctionTemplate());
        when(templateRepo.listByShop("shop-1")).thenReturn(expected);

        List<AuctionTemplate> result = service.listByShop("shop-1");
        assertEquals(expected, result);
    }

    @Test
    void toggleActive_ownerPauses_setsInactive() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        when(templateRepo.getById("t-1")).thenReturn(template);

        service.toggleActive("t-1", "seller-1", false);

        verify(templateRepo).setActive("t-1", false);
        verify(templateRepo, never()).updateNextRunAt(anyString(), anyString());
    }

    @Test
    void toggleActive_ownerResumes_setsActiveAndUpdatesNextRun() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        template.setScheduleType("daily");
        template.setScheduleTime("08:00");
        when(templateRepo.getById("t-1")).thenReturn(template);

        service.toggleActive("t-1", "seller-1", true);

        verify(templateRepo).setActive("t-1", true);
        verify(templateRepo).updateNextRunAt(eq("t-1"), anyString());
    }

    @Test
    void toggleActive_notOwner_throws() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        when(templateRepo.getById("t-1")).thenReturn(template);

        assertThrows(AuctionService.ForbiddenException.class,
                () -> service.toggleActive("t-1", "other-user", true));
    }

    @Test
    void toggleActive_notFound_throws() {
        when(templateRepo.getById("t-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.toggleActive("t-1", "seller-1", true));
    }

    @Test
    void deleteTemplate_ownerDeletes_succeeds() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        when(templateRepo.getById("t-1")).thenReturn(template);

        service.deleteTemplate("t-1", "seller-1");

        verify(templateRepo).delete("t-1");
    }

    @Test
    void deleteTemplate_notOwner_throws() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        when(templateRepo.getById("t-1")).thenReturn(template);

        assertThrows(AuctionService.ForbiddenException.class,
                () -> service.deleteTemplate("t-1", "other-user"));
    }

    @Test
    void deleteTemplate_notFound_throws() {
        when(templateRepo.getById("t-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.deleteTemplate("t-1", "seller-1"));
    }

    @Test
    void processScheduledTemplates_dueTemplate_generatesAuction() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setSellerId("seller-1");
        template.setItemId("item-1");
        template.setItemTitle("Bread");
        template.setShopId("shop-1");
        template.setShopName("Bakery");
        template.setDurationMinutes(60);
        template.setStartBid(500);
        template.setPickupOffsetMinutes(30);
        template.setPickupWindowMinutes(60);
        template.setScheduleType("daily");
        template.setScheduleTime("08:00");
        template.setNextRunAt(Instant.now().minusSeconds(60).toString());
        when(templateRepo.listActive()).thenReturn(List.of(template));

        service.processScheduledTemplates();

        verify(auctionService).createAuction(any(), eq("seller-1"));
        verify(templateRepo).updateNextRunAt(eq("t-1"), anyString());
    }

    @Test
    void processScheduledTemplates_futureTemplate_doesNotGenerate() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setNextRunAt(Instant.now().plusSeconds(3600).toString());
        when(templateRepo.listActive()).thenReturn(List.of(template));

        service.processScheduledTemplates();

        verify(auctionService, never()).createAuction(any(), anyString());
    }

    @Test
    void processScheduledTemplates_blankNextRun_skips() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setNextRunAt("");
        when(templateRepo.listActive()).thenReturn(List.of(template));

        service.processScheduledTemplates();

        verify(auctionService, never()).createAuction(any(), anyString());
    }

    @Test
    void processScheduledTemplates_exceptionInOne_continuesOthers() {
        AuctionTemplate bad = new AuctionTemplate();
        bad.setTemplateId("t-bad");
        bad.setNextRunAt("not-a-date");

        AuctionTemplate good = new AuctionTemplate();
        good.setTemplateId("t-good");
        good.setSellerId("seller-1");
        good.setItemId("item-1");
        good.setDurationMinutes(60);
        good.setPickupOffsetMinutes(30);
        good.setPickupWindowMinutes(60);
        good.setScheduleType("daily");
        good.setScheduleTime("08:00");
        good.setNextRunAt(Instant.now().minusSeconds(60).toString());
        when(templateRepo.listActive()).thenReturn(List.of(bad, good));

        service.processScheduledTemplates();

        verify(auctionService).createAuction(any(), eq("seller-1"));
    }

    @Test
    void computeNextRun_daily_returnsFutureTime() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("daily");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void computeNextRun_weekly_returnsDayInSchedule() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("weekly");
        t.setScheduleDays("MON,WED,FRI");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void computeNextRun_weekly_allDays_returnsFuture() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("weekly");
        t.setScheduleDays("MON,TUE,WED,THU,FRI,SAT,SUN");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void computeNextRun_weekly_emptyDays_returnsFuture() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("weekly");
        t.setScheduleDays("");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void computeNextRun_weekly_invalidDay_ignoresIt() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("weekly");
        t.setScheduleDays("MON,INVALID,FRI");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void computeNextRun_weekly_singleDay_returnsFuture() {
        AuctionTemplate t = new AuctionTemplate();
        t.setScheduleType("weekly");
        t.setScheduleDays("SAT");
        t.setScheduleTime("08:00");

        String nextRun = service.computeNextRun(t);

        assertNotNull(nextRun);
        assertTrue(Instant.parse(nextRun).isAfter(Instant.now()));
    }

    @Test
    void createTemplate_lowercaseScheduleDays_uppercased() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, 30, 60,
                "weekly", "mon,thu,sun", "08:00"
        );

        AuctionTemplate result = service.createTemplate(req, "seller-1");
        assertEquals("MON,THU,SUN", result.getScheduleDays());
    }

    @Test
    void createTemplate_nullOptionalFields_defaultsToEmpty() {
        CreateTemplateRequest req = new CreateTemplateRequest(
                "item-1", null, "shop-1", null,
                0, 0, 0, 0, 0, 0,
                null, null, null, null, 60, 0, 30, 60,
                "daily", null, "08:00"
        );

        AuctionTemplate result = service.createTemplate(req, "seller-1");
        assertEquals("", result.getItemTitle());
        assertEquals("", result.getShopName());
        assertEquals("", result.getImageUrl());
        assertEquals("", result.getDescription());
        assertEquals(1, result.getQuantity());
    }

    @Test
    void processScheduledTemplates_nullNextRun_skips() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        template.setNextRunAt(null);
        when(templateRepo.listActive()).thenReturn(List.of(template));

        service.processScheduledTemplates();

        verify(auctionService, never()).createAuction(any(), anyString());
    }
}
