package com.gavel.bid.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BidTest {

    @Test
    void constructor_setsAllFields() {
        Instant now = Instant.now();
        Bid bid = new Bid("b-1", "a-1", "u-1", "Lamp", "s-1", "Thrift", 500, now, "placed");

        assertThat(bid.getBidId()).isEqualTo("b-1");
        assertThat(bid.getAuctionId()).isEqualTo("a-1");
        assertThat(bid.getUserId()).isEqualTo("u-1");
        assertThat(bid.getItemTitle()).isEqualTo("Lamp");
        assertThat(bid.getShopId()).isEqualTo("s-1");
        assertThat(bid.getShopName()).isEqualTo("Thrift");
        assertThat(bid.getAmount()).isEqualTo(500);
        assertThat(bid.getTimestamp()).isEqualTo(now);
        assertThat(bid.getStatus()).isEqualTo("placed");
    }

    @Test
    void noArgConstructor_createsEmptyBid() {
        Bid bid = new Bid();
        assertThat(bid).isNotNull();
    }

    @Test
    void setters_updateFields() {
        Bid bid = new Bid();
        Instant now = Instant.now();

        bid.setBidId("b-2");
        bid.setAuctionId("a-2");
        bid.setUserId("u-2");
        bid.setItemTitle("Chair");
        bid.setShopId("s-2");
        bid.setShopName("Store");
        bid.setAmount(1000);
        bid.setTimestamp(now);
        bid.setStatus("won");

        assertThat(bid.getBidId()).isEqualTo("b-2");
        assertThat(bid.getAuctionId()).isEqualTo("a-2");
        assertThat(bid.getUserId()).isEqualTo("u-2");
        assertThat(bid.getItemTitle()).isEqualTo("Chair");
        assertThat(bid.getShopId()).isEqualTo("s-2");
        assertThat(bid.getShopName()).isEqualTo("Store");
        assertThat(bid.getAmount()).isEqualTo(1000);
        assertThat(bid.getTimestamp()).isEqualTo(now);
        assertThat(bid.getStatus()).isEqualTo("won");
    }
}
