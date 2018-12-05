package spanner.probes;

import com.google.spanner.v1.CreateSessionRequest;
import com.google.spanner.v1.GetSessionRequest;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.SpannerGrpc;

/** */
public class SpannerProbes {
  private static final String DATABASE =
      "projects/cloudprober-test/instances/test-instance/databases/test-db";
  private static final String CLOUD_API_NAME = "Spanner";
  private static final String TEST_USERNAME = "test_username";

  private SpannerProbes() {}

  public static void sessionManagement(SpannerGrpc.SpannerBlockingStub stub) {

    CreateSessionRequest createRequest =
        CreateSessionRequest.newBuilder().setDatabase(DATABASE).build();
    Session session = stub.createSession(createRequest);
    GetSessionRequest getRequest = GetSessionRequest.newBuilder().build();
    stub.getSession(getRequest);
    System.out.println(session.getName());
  }
}

    /*StreamObserver<Session> responseObserver = new StreamObserver<Session>() {

      @Override
      public void onNext(Session session) {
        System.out.println(session.getName());
        System.out.println("something wrong");

      }

      @Override
      public void onError(Throwable t) {
        System.out.println("something wrong");
      }

      @Override
      public void onCompleted() {
        System.out.println("onCompleted");
      }
    };*/
