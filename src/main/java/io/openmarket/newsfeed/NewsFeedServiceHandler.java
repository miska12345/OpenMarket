package io.openmarket.newsfeed;

import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.model.Item;
import io.openmarket.newsfeed.grpc.NewsFeedProto;
import io.openmarket.newsfeed.grpc.NewsFeedProto.ItemGrpc;
import io.openmarket.newsfeed.grpc.NewsFeedProto.TopDealsRequest;
import io.openmarket.newsfeed.grpc.NewsFeedProto.TopDealsResult;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class NewsFeedServiceHandler {
    private ItemDao itemDao;
    @Inject
    public NewsFeedServiceHandler (@Nonnull ItemDao itemDao) {
        this.itemDao = itemDao;
    }

    public TopDealsResult getTopDeals(TopDealsRequest request) {
        List<Item> items = itemDao.getAllItemsRankedByPurchasedCount(request.getLimit(), request.getCategory());
        List<ItemGrpc> converted = items.stream().map(this::convertToGrpc).collect(Collectors.toList());
        return TopDealsResult.newBuilder().addAllItems(converted).build();
    }



    private NewsFeedProto.ItemGrpc convertToGrpc(Item item) {
        return NewsFeedProto.ItemGrpc.newBuilder()
                .setItemName(item.getItemName()).setItemStock(item.getStock())
                .setBelongTo(item.getBelongTo())
                .setItemPrice(item.getItemPrice())
                .setItemDescription(item.getItemDescription())
                .setItemId(item.getItemID())
                .setCategory(item.getItemCategory())
                .setItemImageLink(item.getItemImageLink())
                .setPurchasedCount(item.getPurchasedCount())
                .build();
    }


}
