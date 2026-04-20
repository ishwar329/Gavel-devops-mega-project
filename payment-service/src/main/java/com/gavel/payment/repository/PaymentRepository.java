package com.gavel.payment.repository;

import com.gavel.payment.model.Payment;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.*;

@Repository
public class PaymentRepository {

    private static final String TABLE_NAME = "payments";
    private final DynamoDbTable<Payment> table;

    public PaymentRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Payment.class));
    }

    public void create(Payment payment) {
        Expression condition = Expression.builder()
                .expression("attribute_not_exists(paymentId)")
                .build();
        try {
            table.putItem(PutItemEnhancedRequest.builder(Payment.class)
                    .item(payment)
                    .conditionExpression(condition)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new PaymentAlreadyExistsException(payment.getPaymentId());
        }
    }

    public Optional<Payment> getById(String paymentId) {
        Payment payment = table.getItem(Key.builder().partitionValue(paymentId).build());
        return Optional.ofNullable(payment);
    }

    public Optional<Payment> getByAuctionId(String auctionId) {
        var index = table.index("auction-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(auctionId).build()))
                .limit(1)
                .build());
        for (var page : results) {
            for (Payment p : page.items()) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public Set<String> getUserIdsWithPaymentForAuction(String auctionId) {
        var index = table.index("auction-index");
        var results = index.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(auctionId).build()));
        Set<String> userIds = new HashSet<>();
        for (var page : results) {
            for (Payment p : page.items()) {
                userIds.add(p.getUserId());
            }
        }
        return userIds;
    }

    public List<Payment> getByUserId(String userId) {
        var index = table.index("user-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(userId).build()))
                .scanIndexForward(false)
                .build());
        List<Payment> payments = new ArrayList<>();
        for (var page : results) {
            payments.addAll(page.items());
        }
        return payments;
    }

    public List<Payment> scanStuck(Instant cutoff) {
        String cutoffStr = cutoff.toString();
        Expression filter = Expression.builder()
                .expression("#s IN (:pending, :processing) AND updatedAt < :cutoff")
                .putExpressionName("#s", "status")
                .putExpressionValue(":pending", AttributeValue.fromS(Payment.STATUS_PENDING))
                .putExpressionValue(":processing", AttributeValue.fromS(Payment.STATUS_PROCESSING))
                .putExpressionValue(":cutoff", AttributeValue.fromS(cutoffStr))
                .build();

        List<Payment> stuck = new ArrayList<>();
        table.scan(r -> r.filterExpression(filter)).forEach(page ->
                stuck.addAll(page.items()));
        return stuck;
    }

    public void updateStatus(String paymentId, String status, String failReason) {
        Payment payment = table.getItem(Key.builder().partitionValue(paymentId).build());
        if (payment == null) return;
        payment.setStatus(status);
        payment.setFailReason(failReason);
        payment.setUpdatedAt(Instant.now().toString());
        table.putItem(payment);
    }

    public void setGatewayDecision(String paymentId, String decision) {
        Payment payment = table.getItem(Key.builder().partitionValue(paymentId).build());
        if (payment == null) return;
        payment.setGatewayDecision(decision);
        payment.setUpdatedAt(Instant.now().toString());
        table.putItem(payment);
    }

    public void incrementRetryCount(String paymentId) {
        Payment payment = table.getItem(Key.builder().partitionValue(paymentId).build());
        if (payment == null) return;
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setUpdatedAt(Instant.now().toString());
        table.putItem(payment);
    }

    public static class PaymentAlreadyExistsException extends RuntimeException {
        public PaymentAlreadyExistsException(String paymentId) {
            super("Payment already exists: " + paymentId);
        }
    }
}
