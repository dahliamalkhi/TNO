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

import org.apache.lucene.codecs.BlockTermState;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.PostingsReaderBase;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.TermState;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;

import javax.crypto.SecretKey;
import java.io.IOException;

/**
 * Concrete class that reads docId(maybe frq,pos,offset,payloads) list
 * with postings format.
 *
 * @see org.apache.lucene.codecs.lucene41.Lucene41SkipReader for details
 * @lucene.experimental
 */
public final class SecureCipherPostingsReader extends PostingsReaderBase {
  private static long StartTime = System.currentTimeMillis();
  private final String segmentName;

  private SecureCipherIndexInput in;
  private final FieldInfos fieldInfos;

  /** Sole constructor. */
  public SecureCipherPostingsReader(Directory dir, FieldInfos fieldInfos, SegmentInfo segmentInfo, IOContext ioContext, String segmentSuffix) throws IOException {
    segmentName = segmentInfo.name;
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherPostingsReader: const: start\r\n");
    this.fieldInfos = fieldInfos;
    String fileName = SecureSimpleTextPostingsFormat.getPostingsFileName(segmentInfo.name, segmentSuffix);
    IndexInput inner_in = dir.openInput(fileName, ioContext);
    boolean success = false;

    try {
      in = new SecureCipherIndexInput(inner_in);
      CodecUtil.checkHeader(in,
          SecureCipherPostingsWriter.DOC_CODEC,
          SecureCipherPostingsWriter.VERSION_CURRENT,
          SecureCipherPostingsWriter.VERSION_CURRENT);

      //this.in = inner_in;
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(in);
      }
    }
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherPostingsReader: const: end\r\n");
  }

  @Override
  public void init(IndexInput termsIn) throws IOException {
    // Make sure we are talking to the matching postings writer
    CodecUtil.checkHeader(termsIn,
                          SecureCipherPostingsWriter.TERMS_CODEC,
                          SecureCipherPostingsWriter.VERSION_CURRENT,
                          SecureCipherPostingsWriter.VERSION_CURRENT);
  }

  // Must keep final because we do non-standard clone
  private final static class IntBlockTermState extends BlockTermState {
    long startFP;

    // Only used by the "primary" TermState -- clones don't
    // copy this (basically they are "transient"):
    ByteArrayDataInput bytesReader;  // TODO: should this NOT be in the TermState...?
    byte[] bytes;

    @Override
    public IntBlockTermState clone() {
      IntBlockTermState other = new IntBlockTermState();
      other.copyFrom(this);
      return other;
    }

    @Override
    public void copyFrom(TermState _other) {
      super.copyFrom(_other);
      IntBlockTermState other = (IntBlockTermState) _other;
      startFP = other.startFP;

      // Do not copy bytes, bytesReader (else TermState is
      // very heavy, ie drags around the entire block's
      // byte[]).  On seek back, if next() is in fact used
      // (rare!), they will be re-read from disk.
    }

    @Override
    public String toString() {
      return super.toString() + " startFP=" + startFP;
    }
  }

  @Override
  public IntBlockTermState newTermState() {
    return new IntBlockTermState();
  }

  @Override
  public void close() throws IOException {
    IOUtils.close(in);
  }

  /* Reads but does not decode the byte[] blob holding
     metadata for the current terms block */
  @Override
  public void readTermsBlock(IndexInput termsIn, FieldInfo fieldInfo, BlockTermState _termState) throws IOException {
    final IntBlockTermState termState = (IntBlockTermState) _termState;

    final int numBytes = termsIn.readVInt();

    if (termState.bytes == null) {
      termState.bytes = new byte[ArrayUtil.oversize(numBytes, 1)];
      termState.bytesReader = new ByteArrayDataInput();
    } else if (termState.bytes.length < numBytes) {
      termState.bytes = new byte[ArrayUtil.oversize(numBytes, 1)];
    }

    termsIn.readBytes(termState.bytes, 0, numBytes);
    termState.bytesReader.reset(termState.bytes, 0, numBytes);
  }

  @Override
  public void nextTerm(FieldInfo fieldInfo, BlockTermState _termState)
    throws IOException {
    final IntBlockTermState termState = (IntBlockTermState) _termState;

    final DataInput in = termState.bytesReader;
    termState.startFP = in.readVLong();
  }

  @Override
  public DocsEnum docs(FieldInfo fieldInfo, BlockTermState termState, Bits liveDocs, DocsEnum reuse, int flags) throws IOException {
    //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherPostingsReader: docs: start " + termState.docFreq + " " + termState.totalTermFreq + "\r\n");
    SecureCipherDocsEnum docsEnum;
    IndexOptions indexOptions = fieldInfo.getIndexOptions();
    if (reuse != null && reuse instanceof SecureCipherDocsEnum && ((SecureCipherDocsEnum) reuse).canReuse(this.in)) {
      docsEnum = (SecureCipherDocsEnum) reuse;
    } else {
      docsEnum = new SecureCipherDocsEnum(in);
    }
    SecretKey fieldKey = SecureCipherUtil.getKey(fieldInfo.name);
    if (fieldKey != null) {
      docsEnum = docsEnum.reset(((IntBlockTermState) termState).startFP, liveDocs, indexOptions == IndexOptions.DOCS_ONLY, termState.docFreq, fieldKey);
    } else {
      docsEnum = null;
    }
    //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherPostingsReader: docs: end " + termState.docFreq + " " + termState.totalTermFreq + "\r\n");
    return docsEnum;
  }

  @Override
  public DocsAndPositionsEnum docsAndPositions(FieldInfo fieldInfo, BlockTermState termState, Bits liveDocs,
                                               DocsAndPositionsEnum reuse, int flags)
      throws IOException {

    IndexOptions indexOptions = fieldInfo.getIndexOptions();
    if (indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
      // Positions were not indexed
      return null;
    }

    //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docsAndPositions: start\r\n");
    SecureCipherDocsAndPositionsEnum docsAndPositionsEnum;
    if (reuse != null && reuse instanceof SecureCipherDocsAndPositionsEnum && ((SecureCipherDocsAndPositionsEnum) reuse).canReuse(this.in)) {
      docsAndPositionsEnum = (SecureCipherDocsAndPositionsEnum) reuse;
    } else {
      docsAndPositionsEnum = new SecureCipherDocsAndPositionsEnum(in);
    }
    SecretKey fieldKey = SecureCipherUtil.getKey(fieldInfo.name);
    if (fieldKey != null) {
      docsAndPositionsEnum = docsAndPositionsEnum.reset(((IntBlockTermState)termState).startFP, liveDocs, indexOptions, termState.docFreq, fieldKey);
    } else {
      docsAndPositionsEnum = null;
    }
    //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docsAndPositions: end\r\n");
    return docsAndPositionsEnum;
  }

  private class SecureCipherDocsEnum extends DocsEnum {
    private final IndexInput inStart;
    private IndexInput in;
    private boolean omitTF;
    private int docID = -1;
    private int tf;
    private Bits liveDocs;
    private int docCount = 0;
    private int current = 0;
    private int cost;

    private final IndexInput insecureIn;

    public SecureCipherDocsEnum(IndexInput in) {
      //this.inStart = SecureCipherFieldsReader.this.in;
      this.inStart = in;
      //this.in = this.inStart.clone();
      this.insecureIn = this.inStart.clone();
    }

    public boolean canReuse(IndexInput in) {
      return in == inStart;
    }

    public SecureCipherDocsEnum reset(long fp, Bits liveDocs, boolean omitTF, int docFreq, SecretKey fieldKey) throws IOException {
      insecureIn.seek(fp);
      IndexInput secureIn = null;
      try {
        secureIn = ((SecureCipherIndexInput)(insecureIn)).startDecryption(fieldKey);
        //secureIn = this.insecureIn;
      } catch (Exception ex) {}
      this.in = secureIn;

      this.liveDocs = liveDocs;

      this.omitTF = omitTF;
      docID = -1;
      tf = 1;
      docCount = docFreq;
      current = 0;
      cost = docFreq;
      return this;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int freq() throws IOException {
      return tf;
    }

    @Override
    public int nextDoc() throws IOException {
      while (current < docCount) {
        if (omitTF) {
          SecureCipherFieldsWriter.DocsOnlyTermValue termValue = new SecureCipherFieldsWriter.DocsOnlyTermValue(in);
          docID = termValue.docID;
        } else {
          SecureCipherFieldsWriter.DocsAndFreqsTermValue termValue = new SecureCipherFieldsWriter.DocsAndFreqsTermValue(in);
          docID = termValue.docID;
          tf = termValue.termDocFreq;
        }
        ++current;

        if ((liveDocs == null || liveDocs.get(docID))) {
          return docID;
        }
      }
      tf = 0;
      return docID = NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) throws IOException {
      // Naive -- better to index skip data
      return slowAdvance(target);
    }

    @Override
    public long cost() {
      return cost;
    }
  }

  private class SecureCipherDocsAndPositionsEnum extends DocsAndPositionsEnum {
    private final IndexInput inStart;
    private IndexInput in;
    private int docID = -1;
    private int tf;
    private Bits liveDocs;
    private int docCount = 0;
    private int current = 0;

    private final BytesRef scratch = new BytesRef(10);
    private final BytesRef scratch2 = new BytesRef(10);
    private final CharsRef scratchUTF16 = new CharsRef(10);
    private final CharsRef scratchUTF16_2 = new CharsRef(10);
    private BytesRef payload;
    private long nextDocStart;
    private boolean readOffsets;
    private boolean readPositions;
    private int startOffset;
    private int endOffset;
    private int cost;

    private final IndexInput insecureIn;

    public SecureCipherDocsAndPositionsEnum(IndexInput in) {
      //this.inStart = SecureCipherFieldsReader.this.in;
      this.inStart = in;
      //this.in = this.inStart.clone();
      this.insecureIn = this.inStart.clone();
    }

    public boolean canReuse(IndexInput in) {
      return in == inStart;
    }

    public SecureCipherDocsAndPositionsEnum reset(long fp, Bits liveDocs, IndexOptions indexOptions, int docFreq, SecretKey fieldKey) throws IOException {
      insecureIn.seek(fp);
      IndexInput secureIn = null;
      try {
        secureIn = ((SecureCipherIndexInput)(insecureIn)).startDecryption(fieldKey);
        //secureIn = this.insecureIn;
      } catch (Exception ex) {}
      this.in = secureIn;

      this.liveDocs = liveDocs;
      nextDocStart = fp;
      docID = -1;
      tf = 0;
      docCount = docFreq;
      current = 0;
      readPositions = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
      readOffsets = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      if (!readOffsets) {
        startOffset = -1;
        endOffset = -1;
      }
      cost = docFreq;
      return this;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int freq() throws IOException {
      return tf;
    }

    @Override
    public int nextDoc() throws IOException {
      assert nextDocStart != 0;

      while (current < docCount) {
        in.seek(nextDocStart);
        SecureCipherFieldsWriter.DocsAndFreqsAndPositionsTermValue termValue = new SecureCipherFieldsWriter.DocsAndFreqsAndPositionsTermValue(in);
        docID = termValue.docID;
        tf = termValue.termDocFreq;

        nextDocStart = termValue.next;
        ++current;

        if ((liveDocs == null || liveDocs.get(docID))) {
          return docID;
        }
      }

      assert nextDocStart == 0;
      tf = 0;
      return docID = NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) throws IOException {
      // Naive -- better to index skip data
      return slowAdvance(target);
    }

    @Override
    public int nextPosition() throws IOException {
      final int pos;
      if (readPositions) {
        pos = in.readInt();
      } else {
        pos = -1;
      }

      if (readOffsets) {
        startOffset = in.readInt();
        endOffset = in.readInt();
      }

      int len = in.readInt();
      if (len > 0) {
        byte[] buf = new byte[len];
        in.readBytes(buf, 0, len);
        payload = new BytesRef(buf);
      }

      return pos;
    }

    @Override
    public int startOffset() throws IOException {
      return startOffset;
    }

    @Override
    public int endOffset() throws IOException {
      return endOffset;
    }

    @Override
    public BytesRef getPayload() {
      return payload;
    }

    @Override
    public long cost() {
      return cost;
    }
  }

  @Override
  public long ramBytesUsed() {
    return 0;
  }

}
