package io.grpc.gcs;

public class ObjectResolver {
  public ObjectResolver(String object, String objectFormat, int objectStart, int objectStop) {
    this.object = object;
    this.objectFormat = objectFormat;
    this.objectStart = objectStart;
    this.objectStop = objectStop;
  }

  public String Resolve(int threadId, int objectId) {
    if (objectFormat == null || objectFormat.equals("")) {
      return object;
    }
    int oid = objectStop == 0 ? objectId : (objectId % (objectStop - objectStart)) + objectStart;
    return objectFormat.replaceAll("\\{t\\}", String.valueOf(threadId)).replaceAll("\\{o\\}",
        String.valueOf(oid));
  }

  private String object;
  private String objectFormat;
  private int objectStart;
  private int objectStop;
}
