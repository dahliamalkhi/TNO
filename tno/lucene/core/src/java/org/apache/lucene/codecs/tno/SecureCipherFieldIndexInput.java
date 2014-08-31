package org.apache.lucene.codecs.tno;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.store.IndexInput;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;

public class SecureCipherFieldIndexInput extends IndexInput {
  private byte[] buffer;
  private int currentPos;

  private Cipher cipher;
  private long cipherLength;
  private long cipherStartFP;
  private long cipherEndFP;

  private SecureCipherFieldIndexInput(SecureCipherFieldIndexInput clone)
  {
    super("SecureCipherFieldIndexInput");
    buffer = clone.buffer;
    cipherLength = clone.cipherLength;
    cipherStartFP = clone.cipherStartFP;
    cipherEndFP = clone.cipherEndFP;
    currentPos = 0;
  }

  public SecureCipherFieldIndexInput(IndexInput in, SecretKey key) throws Exception {
    super("SecureCipherFieldIndexInput");

    //byte[] iv = SecureCipherUtil.readIV(in);
    cipherLength = in.readLong();
    long startPos = in.getFilePointer();
    byte[] encryptedBuf = new byte[(int)cipherLength];
    in.readBytes(encryptedBuf, 0, encryptedBuf.length);
    try {
      if (cipher == null) cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.DECRYPT_MODE, key);
      buffer = cipher.doFinal(encryptedBuf);
      currentPos = 0;
      cipherLength = buffer.length;
      cipherEndFP = in.getFilePointer();
      cipherStartFP = startPos;
    } catch (Exception ex) {
      //throw new Error(ex.toString());
      throw ex;
    }
    in.seek(startPos);
  }

  @Override
  public byte readByte() throws IOException {
    assert buffer != null;
    if (currentPos >= buffer.length) throw new EOFException();
    return buffer[currentPos++];
  }

  @Override
  public void readBytes(byte[] b, int offset, int length) throws IOException {
    assert buffer != null;
    if (currentPos + length > buffer.length) {
      throw new EOFException();
    }
    System.arraycopy(buffer, currentPos, b, offset, length);
    currentPos += length;
  }

  @Override
  public void close() throws IOException {
    buffer = null;
  }

  @Override
  public long length() { throw new NotImplementedException(); }

  @Override
  public long getFilePointer() {
    return cipherStartFP + currentPos;
  }

  @Override
  public void seek(long pos) throws IOException {
    pos -= cipherStartFP;
    if (pos < 0) {
      throw new BufferUnderflowException();
    }
    if (pos >= buffer.length) {
      throw new EOFException();
    }
    currentPos = (int) pos;
  }

  @Override
  public SecureCipherFieldIndexInput clone() {
      return new SecureCipherFieldIndexInput(this);
  }
}
