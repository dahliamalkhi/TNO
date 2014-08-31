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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.IOUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.io.IOException;

/**
 * reads plaintext encrypted stored fields
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SecureCipherStoredFieldsReader extends SecureStoredFieldsReader {
//  private long offsets[]; /* docid -> offset in .sec.fld file */
//  private IndexInput in;
//  private BytesRef scratch = new BytesRef();
//  private CharsRef scratchUTF16 = new CharsRef();
  private final FieldInfos fieldInfos;

  private Cipher cipher = null;

  private StoredFieldsReader reader = null;

  public SecureCipherStoredFieldsReader(Codec codec, Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    this.reader = codec.storedFieldsFormat().fieldsReader(directory, si, fn, context);
    this.fieldInfos = fn;

    try {
      cipher = Cipher.getInstance(SecureCipherStoredFieldsFormat.EncryptionAlgorithm);
    }  catch (Exception ex) {
        try {
          reader.close();
        } catch (Throwable t) {} // ensure we throw our original exception
    }
  }

  // used by clone
  SecureCipherStoredFieldsReader(StoredFieldsReader reader, FieldInfos fieldInfos) {
    this.fieldInfos = fieldInfos;
    this.reader = reader;
    try {
      this.cipher = Cipher.getInstance(SecureCipherStoredFieldsFormat.EncryptionAlgorithm);
    } catch (Exception ex) {
      throw new Error(ex);
    }
  }

  public StoredFieldsReader getStoredFieldsReader() { return reader; }

  class SecureStoredFieldVisitor extends StoredFieldVisitor {

    private final StoredFieldVisitor visitor;
    public SecureStoredFieldVisitor(StoredFieldVisitor visitor) {
      this.visitor = visitor;
    }

    @Override
    public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
      if (ConfigurationUtil.isEncrypted(fieldInfo.name)) {
        readField(value, fieldInfo, visitor);
      } else {
        visitor.binaryField(fieldInfo, value);
      }
    }

    @Override
    public void stringField(FieldInfo fieldInfo, String value) throws IOException {
      visitor.stringField(fieldInfo, value);
    }

    @Override
    public void intField(FieldInfo fieldInfo, int value) throws IOException {
      visitor.intField(fieldInfo, value);
    }

    @Override
    public void longField(FieldInfo fieldInfo, long value) throws IOException {
      visitor.longField(fieldInfo, value);
    }

    @Override
    public void floatField(FieldInfo fieldInfo, float value) throws IOException {
      visitor.floatField(fieldInfo, value);
    }

    @Override
    public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
      visitor.doubleField(fieldInfo, value);
    }

    @Override
    public Status needsField(FieldInfo fieldInfo) throws IOException {
      return visitor.needsField(fieldInfo);
    }
  }
  
  @Override
  public void visitDocument(int n, StoredFieldVisitor visitor) throws IOException {
    reader.visitDocument(n, new SecureStoredFieldVisitor(visitor));
  }
  
  private void readField(byte[] bytes, FieldInfo fieldInfo, StoredFieldVisitor visitor) throws IOException {
    if (!SecureCipherUtil.hasKey(fieldInfo.name)) {
      String value = SecureCipherUtil.encode(bytes, 0, bytes.length);
      visitor.stringField(fieldInfo, value);
      return;
    }

    byte[] decryptedBytes = null;
    try {
      cipher.init(Cipher.DECRYPT_MODE, SecureCipherUtil.getKey(fieldInfo.name), new IvParameterSpec(bytes, 0, 16));
      decryptedBytes = cipher.doFinal(bytes, 16, bytes.length - 16);
    } catch (Exception ex) {
      String value = SecureCipherUtil.encode(bytes, 0, bytes.length);
      visitor.stringField(fieldInfo, value);
      return;
    }

    assert decryptedBytes != null;
    int type = decryptedBytes[0];
    byte[] plainBytes = new byte[decryptedBytes.length-1];
    System.arraycopy(decryptedBytes, 1, plainBytes, 0, decryptedBytes.length-1);
    if (type == SecureCipherStoredFieldsFormat.TYPE_BINARY) {
      visitor.binaryField(fieldInfo, plainBytes);
    } else {
      if (type == SecureCipherStoredFieldsFormat.TYPE_STRING) {
        String value = new String(plainBytes, IOUtils.CHARSET_UTF_8);
        visitor.stringField(fieldInfo, value);
      } else if (type == SecureCipherStoredFieldsFormat.TYPE_INT) {
        int i = ((int)plainBytes[0]) << 24 | ((int)plainBytes[1]) << 16 | ((int)plainBytes[2]) << 8 | ((int)plainBytes[3]);
        visitor.intField(fieldInfo, i);
      } else if (type == SecureCipherStoredFieldsFormat.TYPE_LONG) {
        long l = ((long)plainBytes[0]) << 56 | ((long)plainBytes[1]) << 48 | ((long)plainBytes[2]) << 40 | ((long)plainBytes[3]) << 32;
        l |= ((long)plainBytes[5]) << 24 | ((long)plainBytes[6]) << 16 | ((long)plainBytes[7]) << 8 | ((long)plainBytes[8]);
        visitor.longField(fieldInfo, l);
      } else if (type == SecureCipherStoredFieldsFormat.TYPE_FLOAT) {
        int i = ((int)plainBytes[0]) << 24 | ((int)plainBytes[1]) << 16 | ((int)plainBytes[2]) << 8 | ((int)plainBytes[3]);
        visitor.floatField(fieldInfo, Float.intBitsToFloat(i));
      } else if (type == SecureCipherStoredFieldsFormat.TYPE_DOUBLE) {
        long l = ((long)plainBytes[0]) << 56 | ((long)plainBytes[1]) << 48 | ((long)plainBytes[2]) << 40 | ((long)plainBytes[3]) << 32;
        l |= ((long)plainBytes[5]) << 24 | ((long)plainBytes[6]) << 16 | ((long)plainBytes[7]) << 8 | ((long)plainBytes[8]);
        visitor.doubleField(fieldInfo, Double.longBitsToDouble(l));
      }
    }
  }

  @Override
  public StoredFieldsReader clone() {
    return new SecureCipherStoredFieldsReader(reader.clone(), fieldInfos);
  }
  
  @Override
  public void close() throws IOException { reader.close(); }

  @Override
  public long ramBytesUsed() { return reader.ramBytesUsed(); }
}
