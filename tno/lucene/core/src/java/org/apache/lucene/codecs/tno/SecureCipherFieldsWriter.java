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

import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.PostingsConsumer;
import org.apache.lucene.codecs.TermStats;
import org.apache.lucene.codecs.TermsConsumer;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

class SecureCipherFieldsWriter extends FieldsConsumer {

//  private final IndexOutput keyOut;
  //private final IndexOutput out;
  private final SecureCipherIndexOutput out;
  private CollectionHeader header = null;
  private int fieldCount = 0;

  public ArrayList<FieldHeader> fieldHeaders = new ArrayList<FieldHeader>();

  final static byte[] COLLECTION   = "Collection".getBytes();
  final static byte[] FIELD        = "Field".getBytes();
  final static byte[] TERM         = "Term".getBytes();

  final static class CollectionHeader {
    byte[] preamble = COLLECTION;
    int fieldCount = 0;
    long startFP = -1;
    long fieldsFP = -1;

    CollectionHeader () {
    }

    CollectionHeader (IndexInput in) throws IOException {
      this.read(in);
    }

    void read(IndexInput in) throws IOException {
      byte[] buf = new byte[this.preamble.length];
      in.readBytes(buf, 0, this.preamble.length);
      assert Arrays.equals(buf, this.preamble);

      fieldCount = in.readInt();
      fieldsFP = in.readLong();
    }

    void write(IndexOutput out) throws IOException {
      out.writeBytes(preamble, preamble.length);
      startFP = out.getFilePointer();
      out.writeInt(fieldCount);
      out.writeLong(fieldsFP);
    }

    void finish(IndexOutput out, int fieldCount, long fieldsFP) throws IOException {
      long finish = out.getFilePointer();
      assert(startFP != 0);
      out.seek(startFP);
      out.writeInt(fieldCount);
      out.writeLong(fieldsFP);
      out.seek(finish);
    }
  }

  final static class FieldHeader {
    //byte[] preamble = FIELD;
    String name;
    int termCount = 0;
    int docCount = 0;
    long sumDocFreq = 0;
    long sumTotalTermFreq = 0;
    long termsFP = -1;

    FieldHeader (String name) {
      this.name = name;
    }

    FieldHeader (IndexInput in) throws IOException {
      this.readHeader(in);
    }

    void read(IndexInput in) throws IOException {
//      byte[] buf = new byte[this.preamble.length];
//      in.readBytes(buf, 0, this.preamble.length);
//      assert Arrays.equals(buf, this.preamble);
    }

    void write(IndexOutput out) throws IOException {
      //out.writeBytes(preamble, preamble.length);
    }

    void finish(IndexOutput out, int termCount, int docCount, long sumDocFreq, long sumTotalTermFreq, long termsFP) throws IOException {
      this.termCount = termCount;
      this.docCount = docCount;
      this.sumDocFreq = sumDocFreq;
      this.sumTotalTermFreq = sumTotalTermFreq;
      this.termsFP = termsFP;
    }

    void readHeader(IndexInput in) throws IOException {
      int len = in.readInt();
      byte[] buf = new byte[len];
      in.readBytes(buf, 0, len);
      this.name = new String(buf, "UTF-8");
      this.termCount = in.readInt();
      this.docCount = in.readInt();
      this.sumDocFreq = in.readLong();
      this.sumTotalTermFreq = in.readLong();
      this.termsFP = in.readLong();
    }

    void writeHeader(IndexOutput out) throws IOException {
      byte[] buf = name.getBytes("UTF-8");
      out.writeInt(buf.length);
      out.writeBytes(buf, buf.length);
      out.writeInt(termCount);
      out.writeInt(docCount);
      out.writeLong(sumDocFreq);
      out.writeLong(sumTotalTermFreq);
      out.writeLong(termsFP);
    }
  }

  final static class TermHeader {
    final byte[] preamble = TERM;
    byte[] name;
    int docFreq;
    long totalTermFreq;
    long startFP = -1;

    TermHeader (BytesRef name) {
      this.name = new byte[name.length];
      System.arraycopy(name.bytes, name.offset, this.name, 0, name.length);
    }

    TermHeader (IndexInput in) throws IOException {
      this.readHeader(in);
    }

    void read(IndexInput in) throws IOException {
//      byte[] buf = new byte[this.preamble.length];
//      in.readBytes(buf, 0, this.preamble.length);
//      assert Arrays.equals(buf, this.preamble);
    }

    void write(IndexOutput out) throws IOException {
      startFP = out.getFilePointer();
      //out.writeBytes(preamble, preamble.length);
    }

    void finish(IndexOutput out, int docFreq, long totalTermFreq) throws IOException {
      this.docFreq = docFreq;
      this.totalTermFreq = totalTermFreq;
    }

    void readHeader(IndexInput in) throws IOException {
      int len = in.readInt();
      this.name = new byte[len];
      in.readBytes(this.name, 0, len);
      this.docFreq = in.readInt();
      this.totalTermFreq = in.readLong();
      this.startFP = in.readLong();
    }

    void writeHeader(IndexOutput out) throws IOException {
      out.writeInt(name.length);
      out.writeBytes(name, name.length);
      out.writeInt(docFreq);
      out.writeLong(totalTermFreq);
      out.writeLong(startFP);
    }
  }

  final static class DocsOnlyTermValue {
    int docID;

    DocsOnlyTermValue (int docID) {
      this.docID = docID;
    }

    DocsOnlyTermValue (IndexInput in) throws IOException {
      this.read(in);
    }

    void read(IndexInput in) throws IOException {
      this.docID = in.readInt();
    }

    void write(IndexOutput out) throws IOException {
      out.writeInt(docID);
    }
  }

  final static class DocsAndFreqsTermValue {
    int docID;
    int termDocFreq;

    DocsAndFreqsTermValue (int docID, int termDocFreq) {
      this.docID = docID;
      this.termDocFreq = termDocFreq;
    }

    DocsAndFreqsTermValue (IndexInput in) throws IOException {
      this.read(in);
    }

    void read(IndexInput in) throws IOException {
      this.docID = in.readInt();
      this.termDocFreq = in.readInt();
    }

    void write(IndexOutput out) throws IOException {
      out.writeInt(docID);
      out.writeInt(termDocFreq);
    }
  }

  final static class DocsAndFreqsAndPositionsTermValue {
    int docID;
    int termDocFreq;
    long next = 0;
    long nextFP = 0;

    DocsAndFreqsAndPositionsTermValue (int docID, int termDocFreq) {
      this.docID = docID;
      this.termDocFreq = termDocFreq;
    }

    DocsAndFreqsAndPositionsTermValue (IndexInput in) throws IOException {
      this.read(in);
    }

    void read(IndexInput in) throws IOException {
      this.docID = in.readInt();
      this.termDocFreq = in.readInt();
      this.next = in.readLong();
    }

    void write(IndexOutput out) throws IOException {
      out.writeInt(docID);
      out.writeInt(termDocFreq);
      nextFP = out.getFilePointer();
      out.writeLong(next);
    }

    void finish(IndexOutput out) throws IOException {
      next = out.getFilePointer();
      assert(nextFP != 0);
      out.seek(nextFP);
      out.writeLong(next);
      out.seek(next);
    }
  }

  public SecureCipherFieldsWriter(SegmentWriteState state) throws IOException {
    final String fileName = SecureSimpleTextPostingsFormat.getPostingsFileName(state.segmentInfo.name, state.segmentSuffix);
    IndexOutput inner_out = state.directory.createOutput(fileName, state.context);
    //out = inner_out;
    out = new SecureCipherIndexOutput(inner_out);

    this.header = new CollectionHeader();
    this.header.write(out);
    this.fieldCount = 0;
  }

  @Override
  public TermsConsumer addField(FieldInfo field) throws IOException {
//    if (this.currentField != null) {
//      this.currentField.finish(out);
//    }
//    this.currentField = new FieldHeader(field.name);
//    this.currentField.write(out);
    ++fieldCount;

    return new SecureCipherTermsWriter(field);
  }

  @Override
  public void close() throws IOException {
    try {
      long fieldsFP = out.getFilePointer();
      for (FieldHeader field : fieldHeaders) {
        field.writeHeader(out);
      }
      header.finish(out, fieldCount, fieldsFP);
    } finally {
      out.close();
    }
  }

  private class SecureCipherTermsWriter extends TermsConsumer {
    private final SecureCipherPostingsWriter postingsWriter;
    private FieldHeader fieldHeader;
    private int termCount = 0;

    public SecureCipherTermsWriter(FieldInfo field) throws IOException {
      this.fieldHeader = new FieldHeader(field.name);
      this.fieldHeader.write(out);
      this.termCount = 0;
      //SecretKey fieldKey = SecureCipherUtil.getKey(field.name);
      //out.startEncryption(fieldKey);
      postingsWriter = new SecureCipherPostingsWriter(field);
    }

    @Override
    public PostingsConsumer startTerm(BytesRef term) throws IOException {
      return postingsWriter.startTerm(term);
    }

    @Override
    public void finishTerm(BytesRef term, TermStats stats) throws IOException {
      if (postingsWriter.finishTerm(term, stats)) ++termCount;
    }

    @Override
    public void finish(long sumTotalTermFreq, long sumDocFreq, int docCount) throws IOException {
      long termsFP = out.getFilePointer();
      for (TermHeader term : postingsWriter.termHeaders) {
        term.writeHeader(out);
      }
      fieldHeader.finish(out, termCount, docCount, sumDocFreq, sumTotalTermFreq < 0 ? 0 : sumTotalTermFreq, termsFP);
      fieldHeaders.add(fieldHeader);
      //out.endEncryption();
    }

    @Override
    public Comparator<BytesRef> getComparator() { return BytesRef.getUTF8SortedAsUnicodeComparator(); }
  }

  private class SecureCipherPostingsWriter extends PostingsConsumer {
    private TermHeader termHeader;
    private boolean wroteTerm;

    private final boolean writeTermDocFreqs;
    private final boolean writePositions;
    private final boolean writeOffsets;
    private SecretKey fieldKey;

    private DocsAndFreqsAndPositionsTermValue termValue;

    // for assert:
    private int lastStartOffset = 0;
    private int docCount = 0;

    public ArrayList<TermHeader> termHeaders = new ArrayList<TermHeader>();

    public SecureCipherPostingsWriter(FieldInfo field) {
      IndexOptions indexOptions = field.getIndexOptions();
      writeTermDocFreqs = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
      writePositions = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
      writeOffsets = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;

      fieldKey = SecureCipherUtil.getKey(field.name);

      assert !writeTermDocFreqs;
      assert !writePositions;
      assert !writeOffsets;
    }

    @Override
    public void startDoc(int docID, int termDocFreq) throws IOException {
      if (!wroteTerm) {
        termHeader.write(out);
        out.startEncryption(fieldKey);
        wroteTerm = true;
      }

      if (this.writePositions) {
        termValue = new DocsAndFreqsAndPositionsTermValue(docID, termDocFreq);
        termValue.write(out);
      } else if (writeTermDocFreqs) {
        new DocsAndFreqsTermValue(docID, termDocFreq).write(out);
      } else {
        new DocsOnlyTermValue(docID).write(out);
      }
      lastStartOffset = 0;
      ++docCount;
    }

    public PostingsConsumer startTerm(BytesRef term) throws IOException {
      termHeader = new TermHeader(term);
      wroteTerm = false;
      termValue = null;
      docCount = 0;
      return this;
    }

    public boolean finishTerm(BytesRef term, TermStats stats) throws IOException {
      assert Arrays.equals(term.bytes, termHeader.name);
      assert stats.docFreq == docCount;

      boolean retVal = false;
      if (wroteTerm) {
        out.endEncryption();
        termHeader.finish(out, stats.docFreq, stats.totalTermFreq < 0 ? 0 : stats.totalTermFreq);
        termHeaders.add(this.termHeader);
        retVal = true;
      }
      wroteTerm = false;
      termValue = null;
      docCount = 0;
      return retVal;
    }

    @Override
    public void addPosition(int position, BytesRef payload, int startOffset, int endOffset) throws IOException {
      if (writePositions) {
        out.writeInt(position);
      }

      if (writeOffsets) {
        assert endOffset >= startOffset;
        assert startOffset >= lastStartOffset : "startOffset=" + startOffset + " lastStartOffset=" + lastStartOffset;
        lastStartOffset = startOffset;
        out.writeInt(startOffset);
        out.writeInt(endOffset);
      }

      if (payload != null && payload.length > 0) {
        assert payload.length != 0;
        out.writeInt(payload.length);
        out.writeBytes(payload.bytes, payload.offset, payload.length);
      } else {
        out.writeInt(0);
      }
    }

    @Override
    public void finishDoc() { }
  }
}
