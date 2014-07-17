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

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;

import java.io.EOFException;
import java.io.IOException;

import org.apache.lucene.store.IndexOutput;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.ByteArrayOutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

public class SecureCipherIndexOutput extends IndexOutput {
  private static final int BufSize = 4096;
  private byte[][] buffer;
  private int currentBuf;
  private int currentPos;

  private Cipher cipher;
  private long cipherStartFP = 0;
  private long cipherLength = 0;
  private boolean cipherStarted = false;

  private IndexOutput out;

  public SecureCipherIndexOutput(IndexOutput out) {
    this.out = out;
    buffer = null;
    currentBuf = 0;
    currentPos = 0;
    cipherStartFP = 0;
    cipherLength = 0;
  }

  public void startEncryption(SecretKey key) throws IOException {
    assert !cipherStarted;
    try {
      if (cipher == null) cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      if (buffer == null) buffer = new byte[1][BufSize];
      currentBuf = 0;
      currentPos = 0;
      cipherLength = 0;
      cipherStarted = true;
    } catch (Exception ex) {
      throw new Error(ex.toString());
    }
    //SecureCipherUtil.writeIV(out, cipher.getIV());
    out.writeLong(0);
    cipherStartFP = out.getFilePointer();
    assert buffer[currentBuf] != null;
  }

  public void endEncryption() throws IOException {
    try {
      assert cipherStarted;
      assert cipherLength == (long)currentBuf * (long) BufSize + currentPos;

      byte[] Buf = new byte[currentBuf*BufSize + currentPos];
      for (int b = 0; b < currentBuf; ++b)
        System.arraycopy(buffer[b], 0, Buf, b*BufSize, BufSize);
        //cipher.update(buffer[b]);
      //cipher.update(buffer[currentBuf], 0, currentPos);
      System.arraycopy(buffer[currentBuf], 0, Buf, currentBuf*BufSize, currentPos);
      byte[] encryptedBuf = cipher.doFinal(Buf);
      assert encryptedBuf.length == cipherLength;
      assert cipherStartFP == out.getFilePointer();

      out.seek(out.getFilePointer() - 8);
      out.writeLong(encryptedBuf.length);
      out.writeBytes(encryptedBuf, 0, encryptedBuf.length);

      cipherStarted = false;
      currentBuf = 0;
      currentPos = 0;
      cipherStartFP = 0;
    } catch (Exception ex) {
      throw new Error(ex.toString());
    }
  }

  @Override
  public void writeByte(byte b) throws IOException {
    byte[] buf = new byte[1];
    buf[0] = b;
    writeBytes(buf, 0, 1);
  }


  @Override
  public void writeBytes(byte[] b, int offset, int length) throws IOException {
    if (cipherStarted) {
      while (length > 0) {
        if (currentPos == buffer[currentBuf].length) {
          ++currentBuf;
          currentPos = 0;
          if (currentBuf >= buffer.length) {
            byte[][] newBuffer = new byte[buffer.length * 2][];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            buffer = newBuffer;
          }
          assert currentBuf < buffer.length;
          if (buffer[currentBuf] == null) buffer[currentBuf] = new byte[BufSize];
        }

        assert buffer[currentBuf] != null;
        int bytesToCopy = Math.min(buffer[currentBuf].length - currentPos, length);
        System.arraycopy(b, offset, buffer[currentBuf], currentPos, bytesToCopy);
        offset += bytesToCopy;
        currentPos += bytesToCopy;
        length -= bytesToCopy;

        if ((long)currentBuf*(long)BufSize + currentPos > cipherLength) cipherLength = (long)currentBuf*(long)BufSize + currentPos;
      }
    } else {
      out.writeBytes(b, offset, length);
    }
  }

  @Override
  public void flush() throws IOException {
    out.flush();
  }

  @Override
  public void close() throws IOException {
    assert !cipherStarted;
    this.flush();
    out.close();
  }

  @Override
  public long length() throws IOException {
    return out.length() + cipherLength;
  }

  @Override
  public long getFilePointer() {
    if (cipherStarted) assert cipherStartFP == out.getFilePointer();
    else assert (long)currentBuf * (long)BufSize + currentPos == 0;
    return out.getFilePointer() + (long)currentBuf * (long)BufSize + currentPos;
  }

  @Override
  public void seek(long pos) throws IOException {
    if (cipherStarted) {
      pos -= out.getFilePointer();
      if (pos < 0) {
        throw new BufferUnderflowException();
      }
      if (pos > cipherLength) {
        throw new BufferOverflowException();
      }

      currentBuf = (int)(pos / BufSize);
      currentPos = (int)(pos % BufSize);

      // If pos == 0 the corresponding buffer might not have been allocated already
      // So we reset currentPos and currentBuf to point to the end of the previous buffer
      // If required writeBytes will do the allocation.
      // Note that: allocating a buffer might require the array of buffers to be expanded as well
      // Since this is complex (and could potentially allocate 2x unncessary space) we do it in writeBytes.
      if (pos == cipherLength && buffer[currentBuf] == null) {
        if (currentPos != 0 || currentBuf == 0) {
          System.out.println("FATAL: SecureCipherIndexOutput: seek: wrong state at eof");
          assert false;
        }

        currentPos += BufSize;
        --currentBuf;
      }
      assert buffer[currentBuf] != null;
    }
    else {
      out.seek(pos);
    }
  }
}