package com.gavel.payment.repository;

import com.gavel.payment.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class PaymentRepositoryTest {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Payment> table;
    private DynamoDbIndex<Payment> auctionIndex;
    private DynamoDbIndex<Payment> userIndex;
    private PaymentRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        table = mock(DynamoDbTable.class);
        auctionIndex = mock(DynamoDbIndex.class);
        userIndex = mock(DynamoDbIndex.class);

        when(enhancedClient.table(eq("payments"), any(TableSchema.class))).thenReturn(table);
        when(table.index("auction-index")).thenReturn(auctionIndex);
        when(table.index("user-index")).thenReturn(userIndex);

        repository = new PaymentRepository(enhancedClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_insertsPaymentWithCondition() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");

        repository.create(payment);

        verify(table).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    void create_throwsExceptionWhenPaymentAlreadyExists() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");

        doThrow(ConditionalCheckFailedException.builder().build())
                .when(table).putItem(any(PutItemEnhancedRequest.class));

        assertThatThrownBy(() -> repository.create(payment))
                .isInstanceOf(PaymentRepository.PaymentAlreadyExistsException.class)
                .hasMessageContaining("p-1");
    }

    @Test
    void getById_returnsPaymentWhenExists() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");
        when(table.getItem(any(Key.class))).thenReturn(payment);

        Optional<Payment> result = repository.getById("p-1");

        assertThat(result).isPresent();
        assertThat(result.get().getPaymentId()).isEqualTo("p-1");
    }

    @Test
    void getById_returnsEmptyWhenNotFound() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        Optional<Payment> result = repository.getById("p-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getByAuctionId_returnsFirstPaymentWhenExists() {
        Payment payment = new Payment();
        payment.setAuctionId("a-1");

        PageIterable<Payment> pageIterable = mock(PageIterable.class);
        var page = mock(Page.class);
        when(page.items()).thenReturn(List.of(payment));
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(auctionIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<Payment> result = repository.getByAuctionId("a-1");

        assertThat(result).isPresent();
        assertThat(result.get().getAuctionId()).isEqualTo("a-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getByAuctionId_returnsEmptyWhenNotFound() {
        PageIterable<Payment> pageIterable = mock(PageIterable.class);
        var page = mock(Page.class);
        when(page.items()).thenReturn(List.of());
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(auctionIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<Payment> result = repository.getByAuctionId("a-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserIdsWithPaymentForAuction_returnsAllUserIds() {
        Payment p1 = new Payment();
        p1.setUserId("u-1");
        Payment p2 = new Payment();
        p2.setUserId("u-2");
        Payment p3 = new Payment();
        p3.setUserId("u-1");

        PageIterable<Payment> pageIterable = mock(PageIterable.class);
        var page = mock(Page.class);
        when(page.items()).thenReturn(List.of(p1, p2, p3));
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(auctionIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);

        Set<String> userIds = repository.getUserIdsWithPaymentForAuction("a-1");

        assertThat(userIds).containsExactlyInAnyOrder("u-1", "u-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getByUserId_returnsAllPaymentsForUser() {
        Payment p1 = new Payment();
        p1.setPaymentId("p-1");
        Payment p2 = new Payment();
        p2.setPaymentId("p-2");

        PageIterable<Payment> pageIterable = mock(PageIterable.class);
        var page = mock(Page.class);
        when(page.items()).thenReturn(List.of(p1, p2));
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Payment> payments = repository.getByUserId("u-1");

        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(Payment::getPaymentId).containsExactly("p-1", "p-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scanStuck_returnsPaymentsInPendingOrProcessingStatus() {
        Instant cutoff = Instant.parse("2026-04-20T10:00:00Z");
        Payment p1 = new Payment();
        p1.setPaymentId("p-1");
        p1.setStatus(Payment.STATUS_PENDING);
        Payment p2 = new Payment();
        p2.setPaymentId("p-2");
        p2.setStatus(Payment.STATUS_PROCESSING);

        PageIterable<Payment> pageIterable = mock(PageIterable.class);
        var page = mock(Page.class);
        when(page.items()).thenReturn(List.of(p1, p2));

        doAnswer(invocation -> {
            java.util.function.Consumer<Page<Payment>> action = invocation.getArgument(0);
            action.accept(page);
            return null;
        }).when(pageIterable).forEach(any(java.util.function.Consumer.class));

        when(table.scan(any(java.util.function.Consumer.class))).thenReturn(pageIterable);

        List<Payment> stuck = repository.scanStuck(cutoff);

        assertThat(stuck).hasSize(2);
        assertThat(stuck).extracting(Payment::getPaymentId).containsExactlyInAnyOrder("p-1", "p-2");
    }

    @Test
    void updateStatus_updatesPaymentStatusAndFailReason() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");
        payment.setStatus(Payment.STATUS_PENDING);
        when(table.getItem(any(Key.class))).thenReturn(payment);

        repository.updateStatus("p-1", Payment.STATUS_FAILED, "Insufficient funds");

        verify(table).putItem(argThat((Payment p) ->
                p.getStatus().equals(Payment.STATUS_FAILED) &&
                        p.getFailReason().equals("Insufficient funds") &&
                        p.getUpdatedAt() != null));
    }

    @Test
    void updateStatus_doesNothingWhenPaymentNotFound() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        repository.updateStatus("p-999", Payment.STATUS_FAILED, "error");

        verify(table, never()).putItem(any(Payment.class));
    }

    @Test
    void setGatewayDecision_updatesDecisionField() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");
        when(table.getItem(any(Key.class))).thenReturn(payment);

        repository.setGatewayDecision("p-1", "APPROVE");

        verify(table).putItem(argThat((Payment p) ->
                p.getGatewayDecision().equals("APPROVE") &&
                        p.getUpdatedAt() != null));
    }

    @Test
    void incrementRetryCount_incrementsCountAndUpdatesTimestamp() {
        Payment payment = new Payment();
        payment.setPaymentId("p-1");
        payment.setRetryCount(2);
        when(table.getItem(any(Key.class))).thenReturn(payment);

        repository.incrementRetryCount("p-1");

        verify(table).putItem(argThat((Payment p) ->
                p.getRetryCount() == 3 &&
                        p.getUpdatedAt() != null));
    }

    @Test
    void incrementRetryCount_doesNothingWhenPaymentNotFound() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        repository.incrementRetryCount("p-999");

        verify(table, never()).putItem(any(Payment.class));
    }
}
