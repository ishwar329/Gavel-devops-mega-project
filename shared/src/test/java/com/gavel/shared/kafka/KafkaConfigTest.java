package com.gavel.shared.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {Topics.BID_PLACED, Topics.AUCTION_CLOSED, Topics.PAYMENT_PROCESSED,
                Topics.PAYMENT_FAILED, Topics.REFUND_PROCESSED},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class KafkaConfigTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(KafkaConfig.class)
    static class TestConfig {}

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void publishAndConsume_bidPlacedTopic() throws Exception {
        BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();
        var container = createContainer(uniqueGroup(), Topics.BID_PLACED, records);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 3);

        String marker = UUID.randomUUID().toString();
        String payload = "{\"auction_id\":\"" + marker + "\",\"bid_id\":\"b-1\",\"amount\":500}";
        kafkaTemplate.send(Topics.BID_PLACED, marker, payload).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> received = pollForKey(records, marker, 10);
        assertThat(received).isNotNull();
        assertThat(received.value()).contains("bid_id");
        assertThat(received.value()).contains("500");

        container.stop();
    }

    @Test
    void publishAndConsume_auctionClosedTopic() throws Exception {
        BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();
        var container = createContainer(uniqueGroup(), Topics.AUCTION_CLOSED, records);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 3);

        String marker = UUID.randomUUID().toString();
        String payload = "{\"auction_id\":\"" + marker + "\",\"winner_id\":\"u-1\",\"winning_bid\":1000}";
        kafkaTemplate.send(Topics.AUCTION_CLOSED, marker, payload).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> received = pollForKey(records, marker, 10);
        assertThat(received).isNotNull();
        assertThat(received.value()).contains("winner_id");

        container.stop();
    }

    @Test
    void publishAndConsume_paymentTopics() throws Exception {
        BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();
        var container = createContainer(uniqueGroup(),
                new String[]{Topics.PAYMENT_PROCESSED, Topics.PAYMENT_FAILED, Topics.REFUND_PROCESSED}, records);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 9);

        String m1 = UUID.randomUUID().toString();
        String m2 = UUID.randomUUID().toString();
        String m3 = UUID.randomUUID().toString();
        kafkaTemplate.send(Topics.PAYMENT_PROCESSED, m1, "{\"payment_id\":\"" + m1 + "\"}").get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(Topics.PAYMENT_FAILED, m2, "{\"payment_id\":\"" + m2 + "\"}").get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(Topics.REFUND_PROCESSED, m3, "{\"payment_id\":\"" + m3 + "\"}").get(5, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> received = drainForKeys(records, List.of(m1, m2, m3), 10);
        assertThat(received).hasSize(3);

        container.stop();
    }

    @Test
    void multipleConsumerGroups_eachReceiveMessage() throws Exception {
        BlockingQueue<ConsumerRecord<String, String>> group1Records = new LinkedBlockingQueue<>();
        BlockingQueue<ConsumerRecord<String, String>> group2Records = new LinkedBlockingQueue<>();

        var container1 = createContainer(uniqueGroup(), Topics.BID_PLACED, group1Records);
        var container2 = createContainer(uniqueGroup(), Topics.BID_PLACED, group2Records);
        container1.start();
        container2.start();
        ContainerTestUtils.waitForAssignment(container1, 3);
        ContainerTestUtils.waitForAssignment(container2, 3);

        String marker = UUID.randomUUID().toString();
        kafkaTemplate.send(Topics.BID_PLACED, marker, "{\"test\":\"" + marker + "\"}").get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> r1 = pollForKey(group1Records, marker, 10);
        ConsumerRecord<String, String> r2 = pollForKey(group2Records, marker, 10);

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r1.value()).isEqualTo(r2.value());

        container1.stop();
        container2.stop();
    }

    private String uniqueGroup() {
        return "test-" + UUID.randomUUID();
    }

    private KafkaMessageListenerContainer<String, String> createContainer(
            String groupId, String topic, BlockingQueue<ConsumerRecord<String, String>> records) {
        return createContainer(groupId, new String[]{topic}, records);
    }

    private KafkaMessageListenerContainer<String, String> createContainer(
            String groupId, String[] topics, BlockingQueue<ConsumerRecord<String, String>> records) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties containerProps = new ContainerProperties(topics);
        containerProps.setMessageListener((MessageListener<String, String>) records::add);
        return new KafkaMessageListenerContainer<>(cf, containerProps);
    }

    private ConsumerRecord<String, String> pollForKey(
            BlockingQueue<ConsumerRecord<String, String>> queue, String key, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecord<String, String> r = queue.poll(1, TimeUnit.SECONDS);
            if (r != null && key.equals(r.key())) return r;
        }
        return null;
    }

    private List<ConsumerRecord<String, String>> drainForKeys(
            BlockingQueue<ConsumerRecord<String, String>> queue, List<String> keys, int timeoutSeconds) throws Exception {
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (matched.size() < keys.size() && System.currentTimeMillis() < deadline) {
            ConsumerRecord<String, String> r = queue.poll(1, TimeUnit.SECONDS);
            if (r != null && keys.contains(r.key())) matched.add(r);
        }
        return matched;
    }
}
