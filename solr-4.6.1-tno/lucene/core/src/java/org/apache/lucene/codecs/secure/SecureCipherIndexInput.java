package org.apache.lucene.codecs.secure;

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

public class SecureCipherIndexInput extends IndexInput {
  private byte[] buffer;
  private int currentPos;

  private Cipher cipher;
  private long cipherLength;
  private long cipherStartFP;
  private long cipherEndFP;
  private boolean cipherStarted = false;

  private IndexInput in;

  private SecureCipherIndexInput(SecureCipherIndexInput clone)
  {
    super("SecureCipherIndexInput");
    this.in = clone.in.clone();
    buffer = null;
    currentPos = 0;
    cipherLength = 0;
    cipherStartFP = 0;
    cipherEndFP = 0;
  }

  public SecureCipherIndexInput(IndexInput in) throws IOException {
    super("SecureCipherIndexInput");

    this.in = in;
    buffer = null;
    currentPos = 0;
    cipherLength = 0;
    cipherStartFP = 0;
    cipherEndFP = 0;
  }

  public IndexInput startDecryption(SecretKey key) throws Exception {
    return new SecureCipherFieldIndexInput(in, key);
  }

  @Override
  public byte readByte() throws IOException { return in.readByte(); }

  @Override
  public void readBytes(byte[] b, int offset, int length) throws IOException { in.readBytes(b, offset, length); }

  @Override
  public void close() throws IOException { in.close(); }

  @Override
  public long length() { return in.length(); }

  @Override
  public long getFilePointer() { return in.getFilePointer(); }

  @Override
  public void seek(long pos) throws IOException { in.seek(pos); }

  @Override
  public SecureCipherIndexInput clone() { return new SecureCipherIndexInput(this); }
}
