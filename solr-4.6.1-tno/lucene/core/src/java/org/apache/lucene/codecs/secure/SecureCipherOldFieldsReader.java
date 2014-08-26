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

import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.BytesRefFSTEnum;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PairOutputs;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

class SecureCipherOldFieldsReader extends FieldsProducer {
  private static long StartTime = System.currentTimeMillis();
  private final String segmentName;

  private final TreeMap<String,Long> fields;
  private TreeMap<String, SecureCipherOldFieldsWriter.FieldHeader> fieldHeaders;
  //private final IndexInput in;
  private final SecureCipherIndexInput in;
  private final FieldInfos fieldInfos;

  final static byte[] FIELD        = SecureCipherOldFieldsWriter.FIELD;
  final static byte[] TERM         = SecureCipherOldFieldsWriter.TERM;

  public SecureCipherOldFieldsReader(SegmentReadState state) throws IOException {
    segmentName = state.segmentInfo.name;
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: const: start\r\n");
    fieldInfos = state.fieldInfos;
    String fileName = SecureSimpleTextPostingsFormat.getPostingsFileName(state.segmentInfo.name, state.segmentSuffix);
    IndexInput inner_in = state.directory.openInput(fileName, state.context);
    boolean success = false;
    try {
      in = new SecureCipherIndexInput(inner_in.clone());

      //this.in = inner_in;
      SecureCipherOldFieldsWriter.CollectionHeader header = new SecureCipherOldFieldsWriter.CollectionHeader(in);
      fields = readFields(in, header.fieldCount);
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: const: end\r\n");
  }

  private TreeMap<String,Long> readFields(IndexInput in, int fieldCount) throws IOException {
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: readFields: start\r\n");

    TreeMap<String,Long> fields = new TreeMap<String,Long>();
    TreeMap<String,SecureCipherOldFieldsWriter.FieldHeader> fieldHeaders = new TreeMap<String,SecureCipherOldFieldsWriter.FieldHeader>();

    long fieldStart = in.getFilePointer();
    for (int f = 0; f < fieldCount; ++f) {
      in.seek(fieldStart);
      SecureCipherOldFieldsWriter.FieldHeader fieldHeader = new SecureCipherOldFieldsWriter.FieldHeader(in);
      fields.put(fieldHeader.name, in.getFilePointer());
      fieldHeaders.put(fieldHeader.name, fieldHeader);
      fieldStart = fieldHeader.next;
    }
    this.fieldHeaders = fieldHeaders;
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: readFields: end\r\n");
    return fields;
  }

  private class SecureCipherTermsEnum extends TermsEnum {
    private final IndexOptions indexOptions;
    private int docFreq;
    private long totalTermFreq;
    private long docsStart;
    private boolean ended;
    private BytesRefFSTEnum<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>> fstEnum;
    private HashMap<BytesRef, SecureCipherOldFieldsWriter.TermHeader> map;
    private SecureCipherOldFieldsWriter.TermHeader current = null;
    private final IndexInput in;

    private final SecretKey fieldKey;

    public SecureCipherTermsEnum(FST<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> fst, IndexOptions indexOptions, IndexInput in, SecretKey fieldKey) {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: cons: start\r\n");
      this.indexOptions = indexOptions;
      fstEnum = new BytesRefFSTEnum<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>>(fst);
      this.in = in;
      this.fieldKey = fieldKey;
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: cons: end\r\n");
    }

//    public SecureCipherTermsEnum(HashMap<BytesRef, SecureCipherOldFieldsWriter.TermHeader> map, IndexOptions indexOptions, IndexInput in, SecretKey fieldKey) {
//      this.indexOptions = indexOptions;
//      this.map = map;
//      this.in = in;
//      this.fieldKey = fieldKey;
//    }
//
    @Override
    public boolean seekExact(BytesRef text) throws IOException {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: seekExact: start " + text.utf8ToString() + "\r\n");

      boolean retVal = false;
      if (fstEnum != null) {
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.seekExact(text);
        if (result != null) {
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;
          retVal = true;
        } else {
          retVal = false;
        }
      } else {
        SecureCipherOldFieldsWriter.TermHeader result = map.get(text);
        if (result != null) {
          current = result;
          docsStart = result.nextFP;
          docFreq = result.docFreq;
          totalTermFreq = result.totalTermFreq;
          retVal = true;
        } else {
          retVal = false;
        }
      }
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: seekExact: end " + text.utf8ToString() + "\r\n");
      return retVal;
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: seekCeil: start " + text.utf8ToString() + "\r\n");

      SeekStatus retVal = SeekStatus.END;
      if (fstEnum != null) {
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.seekCeil(text);
        if (result == null) {
          retVal = SeekStatus.END;
        } else {
          //System.out.println("  got text=" + term.utf8ToString());
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;

          if (result.input.equals(text)) {
            retVal = SeekStatus.FOUND;
          } else {
            retVal = SeekStatus.NOT_FOUND;
          }
        }
      } else {
        throw new NotImplementedException();
      }
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: seekCeil: end " + text.utf8ToString() + "\r\n");
      return retVal;
    }

    @Override
    public BytesRef next() throws IOException {
      //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: next: start\r\n");

      BytesRef retVal = null;
      if (fstEnum != null) {
        assert !ended;
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.next();
        if (result != null) {
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;
          retVal = result.input;
        } else {
          retVal = null;
        }
      } else {
        throw new NotImplementedException();
      }
      //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: next: end\r\n");
      return retVal;
    }

    @Override
    public BytesRef term() {
      if (fstEnum != null) {
        return fstEnum.current().input;
      } else {
        return current == null ? null : new BytesRef(current.name);
      }
    }

    @Override
    public long ord() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekExact(long ord) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int docFreq() {
      return docFreq;
    }

    @Override
    public long totalTermFreq() {
      return indexOptions == IndexOptions.DOCS_ONLY ? -1 : totalTermFreq;
    }
 
    @Override
    public DocsEnum docs(Bits liveDocs, DocsEnum reuse, int flags) throws IOException {
      //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docs: start " + term().utf8ToString() + " " + docFreq + " " + totalTermFreq + "\r\n");
      SecureCipherDocsEnum docsEnum;
      if (reuse != null && reuse instanceof SecureCipherDocsEnum && ((SecureCipherDocsEnum) reuse).canReuse(SecureCipherOldFieldsReader.this.in)) {
        docsEnum = (SecureCipherDocsEnum) reuse;
      } else {
        docsEnum = new SecureCipherDocsEnum(in);
      }
      docsEnum = docsEnum.reset(docsStart, liveDocs, indexOptions == IndexOptions.DOCS_ONLY, docFreq, fieldKey);
      //System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docs: end " + term().utf8ToString() + " " + docFreq + " " + totalTermFreq + "\r\n");
      return docsEnum;
    }

    @Override
    public DocsAndPositionsEnum docsAndPositions(Bits liveDocs, DocsAndPositionsEnum reuse, int flags) throws IOException {

      if (indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
        // Positions were not indexed
        return null;
      }

      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docsAndPositions: start\r\n");
      SecureCipherDocsAndPositionsEnum docsAndPositionsEnum;
      if (reuse != null && reuse instanceof SecureCipherDocsAndPositionsEnum && ((SecureCipherDocsAndPositionsEnum) reuse).canReuse(SecureCipherOldFieldsReader.this.in)) {
        docsAndPositionsEnum = (SecureCipherDocsAndPositionsEnum) reuse;
      } else {
        docsAndPositionsEnum = new SecureCipherDocsAndPositionsEnum(in);
      }
      docsAndPositionsEnum = docsAndPositionsEnum.reset(docsStart, liveDocs, indexOptions, docFreq, fieldKey);
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTermsEnum: docsAndPositions: end\r\n");
      return docsAndPositionsEnum;
    }
    
    @Override
    public Comparator<BytesRef> getComparator() {
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }
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
      //this.inStart = SecureCipherOldFieldsReader.this.in;
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
          SecureCipherOldFieldsWriter.DocsOnlyTermValue termValue = new SecureCipherOldFieldsWriter.DocsOnlyTermValue(in);
          docID = termValue.docID;
        } else {
          SecureCipherOldFieldsWriter.DocsAndFreqsTermValue termValue = new SecureCipherOldFieldsWriter.DocsAndFreqsTermValue(in);
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
      //this.inStart = SecureCipherOldFieldsReader.this.in;
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
        SecureCipherOldFieldsWriter.DocsAndFreqsAndPositionsTermValue termValue = new SecureCipherOldFieldsWriter.DocsAndFreqsAndPositionsTermValue(in);
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

  static class TermData {
    public long docsStart;
    public int docFreq;

    public TermData(long docsStart, int docFreq) {
      this.docsStart = docsStart;
      this.docFreq = docFreq;
    }
  }

  private class SecureCipherTerms extends Terms {
    private final long termsStart;
    private final long termsEnd;
    private final FieldInfo fieldInfo;
    private long sumTotalTermFreq;
    private long sumDocFreq;
    private int docCount;
    private FST<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>> fst;
    private HashMap<BytesRef, SecureCipherOldFieldsWriter.TermHeader> map;
    private int termCount;
    private final BytesRef scratch = new BytesRef(10);
    private final CharsRef scratchUTF16 = new CharsRef(10);
    private IndexInput in;

    private final SecretKey fieldKey;

    public SecureCipherTerms(SecureCipherOldFieldsWriter.FieldHeader fieldHeader, long termsStart) throws Exception {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: cons: start\r\n");

      this.termsEnd = fieldHeader.next;
      this.docCount = fieldHeader.docCount;
      this.sumDocFreq = fieldHeader.sumDocFreq;
      this.sumTotalTermFreq = fieldHeader.sumTotalTermFreq;
      fieldInfo = fieldInfos.fieldInfo(fieldHeader.name);

      //SecretKey fieldKey = SecureCipherUtil.getKey(fieldHeader.name);
      fieldKey = SecureCipherUtil.getKey(fieldHeader.name);
      if (fieldKey == null) throw new InvalidKeyException();
      SecureCipherIndexInput secureCipherIndexInput = SecureCipherOldFieldsReader.this.in.clone();
      secureCipherIndexInput.seek(termsStart);
      try {
        //in = secureCipherIndexInput.startDecryption(fieldKey);
        in = secureCipherIndexInput;
        this.termsStart = in.getFilePointer();
        loadTerms(fieldHeader.termCount);
      } catch (Exception ex) {
        secureCipherIndexInput.close();
        if (in != null) in.close();
        throw ex;
      }
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: cons: end\r\n");
    }

    private void loadTerms(int nTerms) throws IOException {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: loadTerms: start " + nTerms + "\r\n");

      PositiveIntOutputs posIntOutputs = PositiveIntOutputs.getSingleton();
      final Builder<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>> b;
      final PairOutputs<Long,Long> outputsInner = new PairOutputs<Long,Long>(posIntOutputs, posIntOutputs);
      final PairOutputs<Long,PairOutputs.Pair<Long,Long>> outputs = new PairOutputs<Long,PairOutputs.Pair<Long,Long>>(posIntOutputs, outputsInner);
      b = new Builder<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>>(FST.INPUT_TYPE.BYTE1, outputs);

      long nextTermFP = termsStart;
      long docsStart = -1;
      long sumDocFreq = 0;
      long sumTotalTermFreq = 0;
      final IntsRef scratchIntsRef = new IntsRef();
      for (int t = 0; t < nTerms; ++t) {
        in.seek(nextTermFP);
        SecureCipherOldFieldsWriter.TermHeader termHeader = new SecureCipherOldFieldsWriter.TermHeader(in);
        docsStart = in.getFilePointer();
        assert docsStart > 0;
        assert termHeader.docFreq > 0;
        assert termHeader.totalTermFreq > 0;
        b.add(Util.toIntsRef(new BytesRef(termHeader.name), scratchIntsRef),
            outputs.newPair(docsStart,
                outputsInner.newPair((long) termHeader.docFreq, termHeader.totalTermFreq)));
        sumDocFreq += termHeader.docFreq;
        sumTotalTermFreq += termHeader.totalTermFreq;
        termCount++;

        nextTermFP = termHeader.next;
      }
      fst = b.finish();

      assert this.sumDocFreq == sumDocFreq;
      assert this.sumTotalTermFreq == sumTotalTermFreq;
      /*
      PrintStream ps = new PrintStream("out.dot");
      fst.toDot(ps);
      ps.close();
      System.out.println("SAVED out.dot");
      */
      //System.out.println("FST " + fst.sizeInBytes());
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: loadTerms: end " + nTerms + "\r\n");
    }
    
    /** Returns approximate RAM bytes used */
    public long ramBytesUsed() {
      return (fst!=null) ? fst.sizeInBytes() : 0;
    }

    @Override
    public TermsEnum iterator(TermsEnum reuse) throws IOException {
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: iterator: start " + reuse + "\r\n");
      TermsEnum termsEnum;
      if (fst != null) {
        //return new SecureCipherTermsEnum(fst, fieldInfo.getIndexOptions(), in);
        termsEnum = new SecureCipherTermsEnum(fst, fieldInfo.getIndexOptions(), in, fieldKey);
//      } else if (map != null) {
//        //return new SecureCipherTermsEnum(map, fieldInfo.getIndexOptions(), in);
//        termsEnum = new SecureCipherTermsEnum(map, fieldInfo.getIndexOptions(), in, fieldKey);
      } else {
        termsEnum = TermsEnum.EMPTY;
      }
      System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherTerms: iterator: end " + reuse + "\r\n");
      return termsEnum;
    }

    @Override
    public Comparator<BytesRef> getComparator() {
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }

    @Override
    public long size() {
      return (long) termCount;
    }

    @Override
    public long getSumTotalTermFreq() {
      return fieldInfo.getIndexOptions() == IndexOptions.DOCS_ONLY ? -1 : sumTotalTermFreq;
    }

    @Override
    public long getSumDocFreq() throws IOException {
      return sumDocFreq;
    }

    @Override
    public int getDocCount() throws IOException {
      return docCount;
    }

    @Override
    public boolean hasFreqs() {
      return fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
    }

    @Override
    public boolean hasOffsets() {
      return fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
    }

    @Override
    public boolean hasPositions() {
      return fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
    }
    
    @Override
    public boolean hasPayloads() {
      return fieldInfo.hasPayloads();
    }
  }

  @Override
  public Iterator<String> iterator() {
    return Collections.unmodifiableSet(fields.keySet()).iterator();
  }

  private final Map<String,SecureCipherTerms> termsCache = new HashMap<String,SecureCipherTerms>();

  @Override
  synchronized public Terms terms(String field) throws IOException {
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: terms: start " + field + "\r\n");
    //Terms terms = null;
    Terms terms = termsCache.get(field);
    if (terms == null) {
      Long fp = fields.get(field);
      SecureCipherOldFieldsWriter.FieldHeader fh = fieldHeaders.get(field);
      if (fp == null) {
        return null;
      } else {
        try {
          terms = new SecureCipherTerms(fh, fp);
          termsCache.put(field, (SecureCipherTerms) terms);
        } catch (Exception ex) {
          System.out.print("Err: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: terms: fail " + field + "" + ex.toString() + "\r\n");
          return null;
        }
      }
    }
    System.out.print("Dbg: " + (System.currentTimeMillis()-StartTime) + " ms: " + segmentName + ": SecureCipherFieldsReader: terms: end " + field + "\r\n");
    return terms;
  }

  @Override
  public int size() {
    return -1;
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public long ramBytesUsed() {
    long sizeInBytes = 0;
//    for(SecureCipherTerms secureCipherTerms : termsCache.values()) {
//      sizeInBytes += (secureCipherTerms!=null) ? secureCipherTerms.ramBytesUsed() : 0;
//    }
    return sizeInBytes;
  }
}
