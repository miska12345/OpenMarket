import io.grpc.ManagedChannelBuilder;

public class CLI {
    public static void main (String[] args) {
        TestClient client = new TestClient(ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build());
        client.pay();
    }
}
