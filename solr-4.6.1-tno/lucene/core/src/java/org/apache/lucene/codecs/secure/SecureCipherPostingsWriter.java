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

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.PostingsWriterBase;
import org.apache.lucene.codecs.TermStats;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsFormat;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMOutputStream;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.packed.PackedInts;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete class that writes docId(maybe frq,pos,offset,payloads) list
 * with postings format.
 *
 * Postings list for each term will be stored separately. 
 *
 * @see org.apache.lucene.codecs.lucene41.Lucene41SkipWriter for details about skipping setting and postings layout.
 * @lucene.experimental
 */
public final class SecureCipherPostingsWriter extends PostingsWriterBase {

  final static String TERMS_CODEC = "SecureCipherPostingsWriterTerms";
  final static String DOC_CODEC = "SecureCipherPostingsWriterDoc";
  //final static String POS_CODEC = "SecureCipherPostingsWriterPos";
  //final static String PAY_CODEC = "SecureCipherPostingsWriterPay";

  final SecureCipherIndexOutput out;
  private IndexOutput termsOut;

  // Increment version to change it
  final static int VERSION_START = 0;
  final static int VERSION_CURRENT = VERSION_START;

  // Holds starting file pointers for each term:
  private long startFP;

  private int lastStartOffset;
  private int docCount;

  private boolean writeTermDocFreqs = false;
  private boolean writePositions = false;
  private boolean writeOffsets = false;
  private SecretKey fieldKey = null;
  private boolean wroteTerm = false;

  private DocsAndFreqsAndPositionsTermValue termValue;

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

  public SecureCipherPostingsWriter(SegmentWriteState state) throws IOException {
    super();

    final String fileName = SecureSimpleTextPostingsFormat.getPostingsFileName(state.segmentInfo.name, state.segmentSuffix);
    IndexOutput inner_out = state.directory.createOutput(fileName, state.context);
    out = new SecureCipherIndexOutput(inner_out);

    boolean success = false;
    try {
      CodecUtil.writeHeader(out, DOC_CODEC, VERSION_CURRENT);
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(out);
      }
    }
  }

  @Override
  public void start(IndexOutput termsOut) throws IOException {
    this.termsOut = termsOut;
    CodecUtil.writeHeader(termsOut, TERMS_CODEC, VERSION_CURRENT);
  }

  @Override
  public void setField(FieldInfo fieldInfo) {
    IndexOptions indexOptions = fieldInfo.getIndexOptions();
    writeTermDocFreqs = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
    writePositions = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
    writeOffsets = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;

    fieldKey = SecureCipherUtil.getKey(fieldInfo.name);

    assert !writeTermDocFreqs;
    assert !writePositions;
    assert !writeOffsets;
  }

  @Override
  public void startTerm() {
    wroteTerm = false;
    termValue = null;
    docCount = 0;
    startFP = out.getFilePointer();
  }

  @Override
  public void startDoc(int docID, int termDocFreq) throws IOException {
    if (!wroteTerm) {
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

  /** Add a new position & payload */
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
  public void finishDoc() throws IOException {  }

  private static class PendingTerm {
    public final long startFP;

    public PendingTerm(long startFP) {
      this.startFP = startFP;
    }
  }
  private final List<PendingTerm> pendingTerms = new ArrayList<PendingTerm>();

  /** Called when we are done adding docs to this term */
  @Override
  public void finishTerm(TermStats stats) throws IOException {
    assert stats.docFreq > 0;
    assert stats.docFreq == docCount: stats.docFreq + " vs " + docCount;

    pendingTerms.add(new PendingTerm(startFP));

    if (wroteTerm) {
      out.endEncryption();
    }
    wroteTerm = false;
    termValue = null;
    docCount = 0;
  }

  private final RAMOutputStream bytesWriter = new RAMOutputStream();

  @Override
  public void flushTermsBlock(int start, int count) throws IOException {

    if (count == 0) {
      termsOut.writeByte((byte) 0);
      return;
    }

    assert start <= pendingTerms.size();
    assert count <= start;

    final int limit = pendingTerms.size() - start + count;

    long lastStartFP = 0;
    for(int idx=limit-count; idx<limit; idx++) {
      PendingTerm term = pendingTerms.get(idx);
//      bytesWriter.writeVLong(term.startFP - lastStartFP);
//      lastStartFP = term.startFP;
      bytesWriter.writeVLong(term.startFP);
    }

    termsOut.writeVInt((int) bytesWriter.getFilePointer());
    bytesWriter.writeTo(termsOut);
    bytesWriter.reset();

    // Remove the terms we just wrote:
    pendingTerms.subList(limit-count, limit).clear();
  }

  @Override
  public void close() throws IOException {
    //IOUtils.close(docOut, posOut, payOut);
    IOUtils.close(out);
  }
}
