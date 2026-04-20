package com.gavel.bid.service;

import com.gavel.bid.model.Bid;
import com.gavel.bid.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BidService {

    private final BidRepository bidRepository;

    public BidService(BidRepository bidRepository) {
        this.bidRepository = bidRepository;
    }

    public void recordBid(Bid bid) {
        bidRepository.markUserPreviousBids(bid.getAuctionId(), bid.getUserId(), bid.getBidId());
        bidRepository.create(bid);
        bidRepository.markOutbid(bid.getAuctionId(), bid.getBidId());
    }

    public void markWinnerBid(String auctionId, String winnerId) {
        bidRepository.markWon(auctionId, winnerId);
    }

    public List<Bid> getAuctionBids(String auctionId) {
        return bidRepository.getByAuction(auctionId);
    }

    public List<Bid> getUserBids(String userId) {
        return bidRepository.getByUser(userId);
    }
}
