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
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//import  org.apache.lucene.codecs.simpletext.SimpleTextUtil;

/**
 * Writes plain-text encrypted stored fields.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SecureCipherStoredFieldsWriter extends SecureStoredFieldsWriter {
  private StoredFieldsWriter writer;
  private Cipher cipher = null;

  public SecureCipherStoredFieldsWriter(Codec codec, Directory directory, SegmentInfo segment, IOContext context) throws IOException {
    this.writer = codec.storedFieldsFormat().fieldsWriter(directory, segment, context);

    try {
      this.cipher = Cipher.getInstance(SecureCipherStoredFieldsFormat.EncryptionAlgorithm);
    } catch (Exception ex) {
      writer.abort();
    }
  }

  @Override
  public void startDocument(int numStoredFields) throws IOException { writer.startDocument(numStoredFields); }

  @Override
  public void finishDocument() throws IOException { writer.finishDocument(); }

  @Override
  public void writeField(FieldInfo info, IndexableField field) throws IOException {
    if (!ConfigurationUtil.isEncrypted(info.name)) {
      writer.writeField(info, field);
    } else {
      int type = 0;
      BytesRef bytes;
      final Number n = field.numericValue();
      if (n != null) {
        int length = 0;
        byte[] value = new byte[8];
        if (n instanceof Byte || n instanceof Short || n instanceof Integer) {
          type = SecureCipherStoredFieldsFormat.TYPE_INT;
          length += 4;
          int i = n.intValue();
          value[0] = (byte)(i >> 24);
          value[1] = (byte)(i >> 16);
          value[2] = (byte)(i >> 8);
          value[3] = (byte) i;
        } else if (n instanceof Long) {
          type = SecureCipherStoredFieldsFormat.TYPE_LONG;
          length += 8;
          long l = n.longValue();
          value[0] = (byte)(l >> 56);
          value[1] = (byte)(l >> 48);
          value[2] = (byte)(l >> 40);
          value[3] = (byte)(l >> 32);
          value[4] = (byte)(l >> 24);
          value[5] = (byte)(l >> 16);
          value[6] = (byte)(l >> 8);
          value[7] = (byte) l;
        } else if (n instanceof Float) {
          type = SecureCipherStoredFieldsFormat.TYPE_FLOAT;
          length += 4;
          int i = Float.floatToIntBits(n.floatValue());
          value[0] = (byte)(i >> 24);
          value[1] = (byte)(i >> 16);
          value[2] = (byte)(i >> 8);
          value[3] = (byte) i;
        } else if (n instanceof Double) {
          type = SecureCipherStoredFieldsFormat.TYPE_DOUBLE;
          length += 8;
          long l = Double.doubleToLongBits(n.doubleValue());
          value[0] = (byte)(l >> 56);
          value[1] = (byte)(l >> 48);
          value[2] = (byte)(l >> 40);
          value[3] = (byte)(l >> 32);
          value[4] = (byte)(l >> 24);
          value[5] = (byte)(l >> 16);
          value[6] = (byte)(l >> 8);
          value[7] = (byte) l;
        } else {
          throw new IllegalArgumentException("cannot store numeric type " + n.getClass());
        }
        bytes = new BytesRef(value, 0, length);
      } else {
        bytes = field.binaryValue();
        if (bytes != null) {
          type = SecureCipherStoredFieldsFormat.TYPE_BINARY;
        } else if (field.stringValue() == null) {
          throw new IllegalArgumentException("field " + field.name() + " is stored but does not have binaryValue, stringValue nor numericValue");
        } else {
          type = SecureCipherStoredFieldsFormat.TYPE_STRING;
          String s = field.stringValue();
          bytes = new BytesRef(10);
          UnicodeUtil.UTF16toUTF8(s, 0, s.length(), bytes);
        }
      }
      assert bytes != null;
      byte[] plainBytes = new byte[bytes.length + 1];
      plainBytes[0] = (byte)type;
      System.arraycopy(bytes.bytes, 0+bytes.offset, plainBytes, 1, bytes.length);
      byte[] encryptedBytes = null;
      int length = 0;
      try {
        SecretKey key = SecureCipherUtil.getKey(field.name());
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        encryptedBytes = new byte[iv.length+cipher.getOutputSize(plainBytes.length)];
        System.arraycopy(iv, 0, encryptedBytes, 0, iv.length);
        length = iv.length;
        length += cipher.doFinal(plainBytes, 0, plainBytes.length, encryptedBytes, iv.length);
      } catch (Exception ex) {
        throw new Error(ex);
      }

      IndexableField encryptedField = new BinaryDocValuesField(field.name(), new BytesRef(encryptedBytes, 0, length));
      writer.writeField(info, encryptedField);
    }
  }

  @Override
  public void finish(FieldInfos fis, int numDocs) throws IOException { writer.finish(fis, numDocs); }

  class WrappedSegmentReader extends SegmentReader {
    private final SegmentReader reader;
    protected WrappedSegmentReader(SegmentReader reader) {
      super();
      this.reader = reader;
    }

    @Override
    public StoredFieldsReader getFieldsReader() {
      return ((SecureCipherStoredFieldsReader)reader.getFieldsReader()).getStoredFieldsReader();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
      throw new NotImplementedException();
    }

    @Override
    public Bits getLiveDocs() {
      throw new NotImplementedException();
    }

    @Override
    protected void doClose() throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public FieldInfos getFieldInfos() {
      throw new NotImplementedException();
    }

    @Override
    public void document(int docID, StoredFieldVisitor visitor) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public boolean hasDeletions() {
      throw new NotImplementedException();
    }

    @Override
    public Fields fields() {
      throw new NotImplementedException();
    }

    @Override
    public int numDocs() {
      throw new NotImplementedException();
    }

    @Override
    public int maxDoc() {
      throw new NotImplementedException();
    }

    @Override
    public TermVectorsReader getTermVectorsReader() {
      throw new NotImplementedException();
    }

    @Override
    public Fields getTermVectors(int docID) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public String toString() {
      throw new NotImplementedException();
    }

    @Override
    public String getSegmentName() {
      throw new NotImplementedException();
    }

    @Override
    public SegmentCommitInfo getSegmentInfo() {
      throw new NotImplementedException();
    }

    @Override
    public Directory directory() {
      throw new NotImplementedException();
    }

    @Override
    public Object getCoreCacheKey() {
      throw new NotImplementedException();
    }

    @Override
    public Object getCombinedCoreAndDeletesKey() {
      throw new NotImplementedException();
    }

    @Override
    public int getTermInfosIndexDivisor() {
      throw new NotImplementedException();
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public Bits getDocsWithField(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public SortedDocValues getSortedDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public NumericDocValues getNormValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public void addCoreClosedListener(CoreClosedListener listener) {
      throw new NotImplementedException();
    }

    @Override
    public void removeCoreClosedListener(CoreClosedListener listener) {
      throw new NotImplementedException();
    }

    @Override
    public long ramBytesUsed() {
      throw new NotImplementedException();
    }
  }

  class WrappedReader extends AtomicReader {
    private final AtomicReader reader;

    public WrappedReader(AtomicReader reader) {
      super();
      this.reader = reader;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
      throw new NotImplementedException();
    }

    @Override
    public String toString() {
      throw new NotImplementedException();
    }

    @Override
    public Fields getTermVectors(int docID) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public int numDocs() {
      throw new NotImplementedException();
    }

    @Override
    public int maxDoc() {
      return reader.maxDoc();
    }

    @Override
    public void document(int docID, StoredFieldVisitor visitor) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public boolean hasDeletions() {
      throw new NotImplementedException();
    }

    @Override
    protected void doClose() throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public Object getCoreCacheKey() {
      throw new NotImplementedException();
    }

    @Override
    public Object getCombinedCoreAndDeletesKey() {
      throw new NotImplementedException();
    }

    @Override
    public Fields fields() throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public SortedDocValues getSortedDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public Bits getDocsWithField(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public NumericDocValues getNormValues(String field) throws IOException {
      throw new NotImplementedException();
    }

    @Override
    public FieldInfos getFieldInfos() {
      throw new NotImplementedException();
    }

    @Override
    public Bits getLiveDocs() {
      return reader.getLiveDocs();
    }
  }

  @Override
  public int merge(MergeState mergeState) throws IOException {
    SegmentReader[] matchingSegmentReaders = mergeState.matchingSegmentReaders;
    SegmentReader[] wrappedSegmentReaders = new SegmentReader[matchingSegmentReaders.length];
    int idx = 0;
    for (SegmentReader matchingReader : mergeState.matchingSegmentReaders) {
      SegmentReader wrappedReader = new WrappedSegmentReader(matchingReader);
      wrappedSegmentReaders[idx++] = wrappedReader;
    }
    List<AtomicReader> matchingReaders = mergeState.readers;
    List<AtomicReader> wrappedReaders = new ArrayList<AtomicReader>(matchingReaders.size());
    idx = 0;
    for (AtomicReader reader : mergeState.readers) {
      AtomicReader wrappedReader = new WrappedReader(reader);
      wrappedReaders.add(idx++,wrappedReader);
    }
    mergeState.readers = wrappedReaders;
    mergeState.matchingSegmentReaders = wrappedSegmentReaders;
    int retVal = writer.merge(mergeState);
    mergeState.matchingSegmentReaders = matchingSegmentReaders;
    mergeState.readers = matchingReaders;
    return retVal;
  }

  @Override
  public void close() throws IOException { writer.close(); }

  @Override
  public void abort() { writer.abort(); }
}
