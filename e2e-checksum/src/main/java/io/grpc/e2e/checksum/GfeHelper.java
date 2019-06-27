/*
 * Copyright 2019 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.grpc.e2e.checksum;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.net.HostAndPort;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.InternalNettyChannelBuilder.OverrideAuthorityChecker;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class GfeHelper {

  static File copyCert(String resourceName) throws IOException {
    URL resourceUrl = DataStoreChecksumClient.class.getClassLoader().getResource(resourceName);
    if (resourceUrl == null) {
      throw new FileNotFoundException(resourceName);
    }
    File file = File.createTempFile("CAcert", "pem");
    file.deleteOnExit();
    try (BufferedReader in =
        new BufferedReader(new InputStreamReader(resourceUrl.openStream(), UTF_8));
        Writer out = new OutputStreamWriter(new FileOutputStream(file), UTF_8)) {
      String line;
      do {
        while ((line = in.readLine()) != null) {
          if ("-----BEGIN CERTIFICATE-----".equals(line)) {
            break;
          }
        }
        out.append(line);
        out.append("\n");
        while ((line = in.readLine()) != null) {
          out.append(line);
          out.append("\n");
          if ("-----END CERTIFICATE-----".equals(line)) {
            break;
          }
        }
      } while (line != null);
    }
    return file;
  }

  static ManagedChannelBuilder<?> getChannelBuilderForTestGFE(
      String host, int sslPort, String certPath, String hostInCert) {
    SslContext sslContext;
    try {
      // Use the test GFE certificate authority, override ciphers for Java 7 compatibility.
      sslContext =
          GrpcSslContexts.forClient().trustManager(copyCert(certPath)).ciphers(null).build();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }

    HostAndPort hostPort = HostAndPort.fromParts(host, sslPort);
    String target;
    try {
      target = new URI("dns", "", "/" + hostPort, null).toString();
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
    try {
      NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(target);
      InternalNettyChannelBuilder.overrideAuthorityChecker(
          channelBuilder,
          new OverrideAuthorityChecker() {
            @Override
            public String checkAuthority(String authority) {
              return authority;
            }
          });
      return channelBuilder
          .overrideAuthority(hostInCert)
          .sslContext(sslContext)
          .negotiationType(NegotiationType.TLS);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
