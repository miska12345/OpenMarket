package io.openmarket.server;

import io.grpc.ManagedChannelBuilder;

public class CLI {
    public static void main(String[] args) {
//        TestClientEvent client = new TestClientEvent(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build());
////        client.create();
//        //client.delete("a3e15341-51b5-4fb5-93af-ae9c5ab7bc75");
//        client.doRedeem("fdfc7978-e80b-4e27-a934-25918267e656");

        TestClient client = new TestClient(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build());
        client.doGetWallet();
    }
}
