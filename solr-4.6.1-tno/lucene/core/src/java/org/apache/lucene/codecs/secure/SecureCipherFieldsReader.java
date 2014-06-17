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
import java.util.TreeMap;

class SecureCipherFieldsReader extends FieldsProducer {
  private final TreeMap<String,Long> fields;
  private TreeMap<String, SecureCipherFieldsWriter.FieldHeader> fieldHeaders;
  //private final IndexInput in;
  private final SecureCipherIndexInput in;
  private final FieldInfos fieldInfos;

  final static byte[] FIELD        = SecureCipherFieldsWriter.FIELD;
  final static byte[] TERM         = SecureCipherFieldsWriter.TERM;

  public SecureCipherFieldsReader(SegmentReadState state) throws IOException {
    fieldInfos = state.fieldInfos;
    String fileName = SecureSimpleTextPostingsFormat.getPostingsFileName(state.segmentInfo.name, state.segmentSuffix);
    IndexInput inner_in = state.directory.openInput(fileName, state.context);
    boolean success = false;
    try {
      in = new SecureCipherIndexInput(inner_in.clone());

      //this.in = inner_in;
      SecureCipherFieldsWriter.CollectionHeader header = new SecureCipherFieldsWriter.CollectionHeader(in);
      //IndexInput keyIn = state.directory.openInput(fileName + ".key", state.context);
      //readKeys(keyIn, header.fieldCount);
      fields = readFields(in, header.fieldCount);
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
  }

//  private void readKeys(IndexInput keyIn, int fieldCount) throws IOException {
//    SecretKey masterKey = SecureCipherUtil.readKey(keyIn);
//    for (int f = 0; f < fieldCount; ++f) {
//      String field = keyIn.readString();
//      keyIn.readByte();
//      SecretKey fieldKey = SecureCipherUtil.readKey(keyIn);
//      SecureCipherUtil.addKey(field, fieldKey);
//    }
//  }

  private TreeMap<String,Long> readFields(IndexInput in, int fieldCount) throws IOException {
    TreeMap<String,Long> fields = new TreeMap<String,Long>();
    TreeMap<String,SecureCipherFieldsWriter.FieldHeader> fieldHeaders = new TreeMap<String,SecureCipherFieldsWriter.FieldHeader>();

    long fieldStart = in.getFilePointer();
    for (int f = 0; f < fieldCount; ++f) {
      in.seek(fieldStart);
      SecureCipherFieldsWriter.FieldHeader fieldHeader = new SecureCipherFieldsWriter.FieldHeader(in);
      fields.put(fieldHeader.name, in.getFilePointer());
      fieldHeaders.put(fieldHeader.name, fieldHeader);
      fieldStart = fieldHeader.next;
    }
    this.fieldHeaders = fieldHeaders;
    return fields;
  }

  private class SecureCipherTermsEnum extends TermsEnum {
    private final IndexOptions indexOptions;
    private int docFreq;
    private long totalTermFreq;
    private long docsStart;
    private boolean ended;
    private BytesRefFSTEnum<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>> fstEnum;
    private HashMap<BytesRef, SecureCipherFieldsWriter.TermHeader> map;
    private SecureCipherFieldsWriter.TermHeader current = null;
    private final IndexInput in;

    public SecureCipherTermsEnum(FST<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> fst, IndexOptions indexOptions, IndexInput in) {
      this.indexOptions = indexOptions;
      fstEnum = new BytesRefFSTEnum<PairOutputs.Pair<Long,PairOutputs.Pair<Long,Long>>>(fst);
      this.in = in;
    }

    public SecureCipherTermsEnum(HashMap<BytesRef, SecureCipherFieldsWriter.TermHeader> map, IndexOptions indexOptions, IndexInput in) {
      this.indexOptions = indexOptions;
      this.map = map;
      this.in = in;
    }

    @Override
    public boolean seekExact(BytesRef text) throws IOException {

      if (fstEnum != null) {
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.seekExact(text);
        if (result != null) {
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;
          return true;
        } else {
          return false;
        }
      } else {
        SecureCipherFieldsWriter.TermHeader result = map.get(text);
        if (result != null) {
          current = result;
          docsStart = result.nextFP;
          docFreq = result.docFreq;
          totalTermFreq = result.totalTermFreq;
          return true;
        } else {
          return false;
        }
      }
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {

      //System.out.println("seek to text=" + text.utf8ToString());
      if (fstEnum != null) {
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.seekCeil(text);
        if (result == null) {
          //System.out.println("  end");
          return SeekStatus.END;
        } else {
          //System.out.println("  got text=" + term.utf8ToString());
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;

          if (result.input.equals(text)) {
            //System.out.println("  match docsStart=" + docsStart);
            return SeekStatus.FOUND;
          } else {
            //System.out.println("  not match docsStart=" + docsStart);
            return SeekStatus.NOT_FOUND;
          }
        }
      } else {
        throw new NotImplementedException();
      }
    }

    @Override
    public BytesRef next() throws IOException {
      if (fstEnum != null) {
        assert !ended;
        final BytesRefFSTEnum.InputOutput<PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>>> result = fstEnum.next();
        if (result != null) {
          PairOutputs.Pair<Long, PairOutputs.Pair<Long, Long>> pair1 = result.output;
          PairOutputs.Pair<Long, Long> pair2 = pair1.output2;
          docsStart = pair1.output1;
          docFreq = pair2.output1.intValue();
          totalTermFreq = pair2.output2;
          return result.input;
        } else {
          return null;
        }
      } else {
        throw new NotImplementedException();
      }
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
      SecureCipherDocsEnum docsEnum;
      if (reuse != null && reuse instanceof SecureCipherDocsEnum && ((SecureCipherDocsEnum) reuse).canReuse(SecureCipherFieldsReader.this.in)) {
        docsEnum = (SecureCipherDocsEnum) reuse;
      } else {
        docsEnum = new SecureCipherDocsEnum(in);
      }
      return docsEnum.reset(docsStart, liveDocs, indexOptions == IndexOptions.DOCS_ONLY, docFreq);
    }

    @Override
    public DocsAndPositionsEnum docsAndPositions(Bits liveDocs, DocsAndPositionsEnum reuse, int flags) throws IOException {

      if (indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
        // Positions were not indexed
        return null;
      }

      SecureCipherDocsAndPositionsEnum docsAndPositionsEnum;
      if (reuse != null && reuse instanceof SecureCipherDocsAndPositionsEnum && ((SecureCipherDocsAndPositionsEnum) reuse).canReuse(SecureCipherFieldsReader.this.in)) {
        docsAndPositionsEnum = (SecureCipherDocsAndPositionsEnum) reuse;
      } else {
        docsAndPositionsEnum = new SecureCipherDocsAndPositionsEnum(in);
      } 
      return docsAndPositionsEnum.reset(docsStart, liveDocs, indexOptions, docFreq);
    }
    
    @Override
    public Comparator<BytesRef> getComparator() {
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }
  }

  private class SecureCipherDocsEnum extends DocsEnum {
    private final IndexInput inStart;
    private final IndexInput in;
    private boolean omitTF;
    private int docID = -1;
    private int tf;
    private Bits liveDocs;
    private int docCount = 0;
    private int current = 0;
    private int cost;
    
    public SecureCipherDocsEnum(IndexInput in) {
      //this.inStart = SecureCipherFieldsReader.this.in;
      this.inStart = in;
      this.in = this.inStart.clone();
    }

    public boolean canReuse(IndexInput in) {
      return in == inStart;
    }

    public SecureCipherDocsEnum reset(long fp, Bits liveDocs, boolean omitTF, int docFreq) throws IOException {
      this.liveDocs = liveDocs;
      in.seek(fp);
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
    private final IndexInput in;
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

    public SecureCipherDocsAndPositionsEnum(IndexInput in) {
      //this.inStart = SecureCipherFieldsReader.this.in;
      this.inStart = in;
      this.in = this.inStart.clone();
    }

    public boolean canReuse(IndexInput in) {
      return in == inStart;
    }

    public SecureCipherDocsAndPositionsEnum reset(long fp, Bits liveDocs, IndexOptions indexOptions, int docFreq) {
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
    private HashMap<BytesRef, SecureCipherFieldsWriter.TermHeader> map;
    private int termCount;
    private final BytesRef scratch = new BytesRef(10);
    private final CharsRef scratchUTF16 = new CharsRef(10);
    private IndexInput in;

    public SecureCipherTerms(SecureCipherFieldsWriter.FieldHeader fieldHeader, long termsStart) throws Exception {
      this.termsEnd = fieldHeader.next;
      this.docCount = fieldHeader.docCount;
      this.sumDocFreq = fieldHeader.sumDocFreq;
      this.sumTotalTermFreq = fieldHeader.sumTotalTermFreq;
      fieldInfo = fieldInfos.fieldInfo(fieldHeader.name);

      SecretKey fieldKey = SecureCipherUtil.getKey(fieldHeader.name);
      if (fieldKey == null) throw new InvalidKeyException();
      SecureCipherIndexInput secureCipherIndexInput = SecureCipherFieldsReader.this.in.clone();
      secureCipherIndexInput.seek(termsStart);
      try {
        in = secureCipherIndexInput.startDecryption(fieldKey);
        this.termsStart = in.getFilePointer();
        loadTerms(fieldHeader.termCount);
      } catch (Exception ex) {
        secureCipherIndexInput.close();
        if (in != null) in.close();
        throw ex;
      }
    }

    private void loadTerms(int nTerms) throws IOException {
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
      //map = new HashMap<BytesRef, SecureCipherFieldsWriter.TermHeader>();
      for (int t = 0; t < nTerms; ++t) {
      //while (nextTermFP < termsEnd) {
        in.seek(nextTermFP);
        SecureCipherFieldsWriter.TermHeader termHeader = new SecureCipherFieldsWriter.TermHeader(in);
        docsStart = in.getFilePointer();
        assert docsStart > 0;
        assert termHeader.docFreq > 0;
        assert termHeader.totalTermFreq > 0;
        b.add(Util.toIntsRef(new BytesRef(termHeader.name), scratchIntsRef),
            outputs.newPair(docsStart,
                outputsInner.newPair((long) termHeader.docFreq, termHeader.totalTermFreq)));
        //termHeader.nextFP = docsStart;
        //map.put(new BytesRef(termHeader.name), termHeader);
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
    }
    
    /** Returns approximate RAM bytes used */
    public long ramBytesUsed() {
      return (fst!=null) ? fst.sizeInBytes() : 0;
    }

    @Override
    public TermsEnum iterator(TermsEnum reuse) throws IOException {
      if (fst != null) {
        return new SecureCipherTermsEnum(fst, fieldInfo.getIndexOptions(), in);
      } else if (map != null) {
        return new SecureCipherTermsEnum(map, fieldInfo.getIndexOptions(), in);
      }else {
        return TermsEnum.EMPTY;
      }
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

  //private final Map<String,SecureCipherTerms> termsCache = new HashMap<String,SecureCipherTerms>();

  @Override
  synchronized public Terms terms(String field) throws IOException {
    Terms terms = null;
    //Terms terms = termsCache.get(field);
    if (terms == null) {
      Long fp = fields.get(field);
      SecureCipherFieldsWriter.FieldHeader fh = fieldHeaders.get(field);
      if (fp == null) {
        return null;
      } else {
        try {
          terms = new SecureCipherTerms(fh, fp);
          //termsCache.put(field, (SecureCipherTerms) terms);
        } catch (Exception ex) {
          return null;
        }
      }
    }
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
