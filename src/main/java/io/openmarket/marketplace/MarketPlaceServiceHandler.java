package io.openmarket.marketplace;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Message;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.grpc.MarketPlaceProto.*;
import io.openmarket.marketplace.model.Item;

import javax.inject.Inject;
import java.util.*;

import static io.openmarket.config.MerchandiseConfig.*;

public class MarketPlaceServiceHandler {
    private final ItemDao itemDao;

    @Inject
    public MarketPlaceServiceHandler(ItemDao itemDao) {
        this.itemDao = itemDao;
    }
//    public String getOwner()


    public GetOrgItemsResult getListingByOrgId(GetOrgItemsRequest request) {
        List<String> itemIds = this.itemDao.getItemIdsByOrg(request.getOrgId());
        List<Item> selling = this.itemDao.batchLoad(itemIds);
        List<ItemGrpc> result = new ArrayList<>();

        for(Item item : selling) {
            result.add(
                    ItemGrpc.newBuilder()
                    .setItemName(item.getItemName()).setItemStock(item.getStock())
                    .setBelongTo(item.getBelongTo())
                    .setItemPrice(item.getItemPrice())
                    .setItemDescription(item.getItemDescription())
                    .setItemId(item.getItemID())
                    .setItemImageLink(item.getItemImageLink())
                    .build()
            );
        }

        return GetOrgItemsResult.newBuilder().addAllItems(result).build();
    }

//    final UpdateItemRequest updateRequest = new UpdateItemRequest().withTableName(MER_DDB_TABLE_NAME)
//            .withKey(ImmutableMap.of(MER_DDB_ATTRIBUTE_ID, new AttributeValue(itemId)))
//            .withUpdateExpression("SET #count = #count - :purchased")
//            .withConditionExpression("#count >= :purchased")
//            .withExpressionAttributeNames(
//                    ImmutableMap.of("#count", MER_DDB_ATTRIBUTE_STOCK)
//            ).withExpressionAttributeValues(
//                    ImmutableMap.of(":purchased", new AttributeValue().withN(String.valueOf(count)))
//            );

    public void checkout(CheckOutRequest request) {
        List<ItemGrpc> items = request.getItemsList();
        List<Integer> itemCounts = request.getCountList();
        Double total = 0.0;
        if (items.size() != itemCounts.size()  || items.isEmpty())
            throw new IllegalArgumentException("Invalid order");

        for(int i = 0; i < items.size(); i++) {
            total += this.itemDao.load(items.get(i).getItemId())
                    .get().getItemPrice() * itemCounts.get(i);
        }

        //issue transaction

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {

            }
        }, 1000);

    }

}
