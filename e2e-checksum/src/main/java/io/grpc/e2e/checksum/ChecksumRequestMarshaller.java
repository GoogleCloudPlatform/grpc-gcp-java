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

import com.google.protobuf.CodedOutputStream;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.internal.IoUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Checksum;

public class ChecksumRequestMarshaller<ReqT> implements Marshaller<ReqT> {
  private static final Logger logger = Logger.getLogger(ChecksumRequestMarshaller.class.getName());
  private Marshaller<ReqT> delegate;
  private final int CHECKSUM_FIELD_NUMBER = 2047;
  private final int CHECKSUM_OVERHEAD_BYTES = 6;

  private String printBytes(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x ", b));
    }
    return sb.toString();
  }

  public ChecksumRequestMarshaller(Marshaller<ReqT> delegate) {
    this.delegate = delegate;
  }

  @Override
  public InputStream stream(ReqT value) {
    InputStream stream = delegate.stream(value);
    byte[] payload = null;
    try {
      payload = IoUtils.toByteArray(stream);
      // logger.info("original payload bytes: " + printBytes(payload));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (payload.length == 0) {
      return null;
    }

    try {
      logger.info("Calculating checksum on payload...");
      // reserve CHECKSUM_OVERHEAD_BYTES bytes for tag+checksum
      byte[] resultBytes = new byte[CHECKSUM_OVERHEAD_BYTES + payload.length];

      // calculate crc32 checksum for payload
      Checksum checksum = new Crc32c();
      checksum.update(payload, 0, payload.length);

      // prepend tag+checksum to payload bytes.
      CodedOutputStream codedOutputStream =
          CodedOutputStream.newInstance(resultBytes, 0, CHECKSUM_OVERHEAD_BYTES);
      codedOutputStream.writeFixed32(CHECKSUM_FIELD_NUMBER, (int) checksum.getValue());

      // copy payload bytes to result bytes.
      System.arraycopy(payload, 0, resultBytes, CHECKSUM_OVERHEAD_BYTES, payload.length);
      // logger.info("payload bytes after checksum: " + printBytes(resultBytes));
      logger.info("Checksum overhead prepended to request payload.");
      return new ByteArrayInputStream(resultBytes);
    } catch (IOException e) {
      // checksum process failed, use original payload pytes.
      logger.log(Level.WARNING, "Checksum calculation failed, forwarding original payload.", e);
      return new ByteArrayInputStream(payload);
    }
  }

  @Override
  public ReqT parse(InputStream stream) {
    return delegate.parse(stream);
  }
}
