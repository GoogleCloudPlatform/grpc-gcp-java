import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.spanner.v1.SpannerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import spanner.probes.SpannerProbes;

/** */
public class Prober {

  private static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final String SPANNER_TARGET = "spanner.googleapis.com";

  private Prober() {}

  private static SpannerGrpc.SpannerBlockingStub getStubChannel() {

    //
    GoogleCredentials creds;
    try {
      creds = GoogleCredentials.getApplicationDefault();
    } catch (Exception e) {
      System.out.println(e.getMessage());
      return null;
    }
    ImmutableList<String> requiredScopes = ImmutableList.of(OAUTH_SCOPE);
    creds = creds.createScoped(requiredScopes);

    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(SPANNER_TARGET, 443).build();
    SpannerGrpc.SpannerBlockingStub stub =
        SpannerGrpc.newBlockingStub(channel).withCallCredentials(MoreCallCredentials.from(creds));
    return stub;
  }

  private static void excuteProbe() {
    SpannerGrpc.SpannerBlockingStub stub = getStubChannel();
    SpannerProbes.sessionManagement(stub);
  }

  public static void main(String[] args) {
    excuteProbe();
  }
}
