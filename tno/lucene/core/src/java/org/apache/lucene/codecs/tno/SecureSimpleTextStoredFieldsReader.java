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

import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.UnicodeUtil;

import java.io.IOException;

//import  org.apache.lucene.codecs.simpletext.SimpleTextUtil;


/**
 * reads plaintext encrypted stored fields
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SecureSimpleTextStoredFieldsReader extends SecureStoredFieldsReader {
  private long offsets[]; /* docid -> offset in .sec.fld file */
  private IndexInput in;
  private BytesRef scratch = new BytesRef();
  private CharsRef scratchUTF16 = new CharsRef();
  private final FieldInfos fieldInfos;

  public SecureSimpleTextStoredFieldsReader(Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    this.fieldInfos = fn;
    boolean success = false;
    try {
      in = directory.openInput(IndexFileNames.segmentFileName(si.name, "", SecureSimpleTextStoredFieldsWriter.FIELDS_EXTENSION), context);
      success = true;
    } finally {
      if (!success) {
        try {
          close();
        } catch (Throwable t) {} // ensure we throw our original exception
      }
    }
    readIndex(si.getDocCount());
  }

  // used by clone
  SecureSimpleTextStoredFieldsReader(long offsets[], IndexInput in, FieldInfos fieldInfos) {
    this.offsets = offsets;
    this.in = in;
    this.fieldInfos = fieldInfos;
  }
  
  // we don't actually write a .fdx-like index, instead we read the 
  // stored fields file in entirety up-front and save the offsets 
  // so we can seek to the documents later.
  private void readIndex(int size) throws IOException {
    offsets = new long[size];
    int upto = 0;
    while (!scratch.equals(SecureSimpleTextStoredFieldsWriter.END)) {
      readLine();
      if (StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.DOC)) {
        offsets[upto] = in.getFilePointer();
        upto++;
      }
    }
    assert upto == offsets.length;
  }
  
  @Override
  public void visitDocument(int n, StoredFieldVisitor visitor) throws IOException {
    in.seek(offsets[n]);
    readLine();
    assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.NUM);
    int numFields = parseIntAt(SecureSimpleTextStoredFieldsWriter.NUM.length);
    
    for (int i = 0; i < numFields; i++) {
      readLine();
      assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.FIELD);
      int fieldNumber = parseIntAt(SecureSimpleTextStoredFieldsWriter.FIELD.length);
      FieldInfo fieldInfo = fieldInfos.fieldInfo(fieldNumber);
      readLine();
      assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.NAME);
      readLine();
      assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.TYPE);
      
      final BytesRef type;
      if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_STRING, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_STRING;
      } else if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_BINARY, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_BINARY;
      } else if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_INT, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_INT;
      } else if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_LONG, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_LONG;
      } else if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_FLOAT, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_FLOAT;
      } else if (equalsAt(SecureSimpleTextStoredFieldsWriter.TYPE_DOUBLE, scratch, SecureSimpleTextStoredFieldsWriter.TYPE.length)) {
        type = SecureSimpleTextStoredFieldsWriter.TYPE_DOUBLE;
      } else {
        throw new RuntimeException("unknown field type");
      }

      switch (visitor.needsField(fieldInfo)) {
        case YES:
          readField(type, fieldInfo, visitor);
          break;
        case NO:   
          readLine();
          assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.VALUE);
          break;
        case STOP: return;
      }
    }
  }
  
  private void readField(BytesRef type, FieldInfo fieldInfo, StoredFieldVisitor visitor) throws IOException {
    readLine();
    assert StringHelper.startsWith(scratch, SecureSimpleTextStoredFieldsWriter.VALUE);
    if (type == SecureSimpleTextStoredFieldsWriter.TYPE_STRING) {
      visitor.stringField(fieldInfo, new String(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length, "UTF-8"));
    } else if (type == SecureSimpleTextStoredFieldsWriter.TYPE_BINARY) {
      byte[] copy = new byte[scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length];
      System.arraycopy(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, copy, 0, copy.length);
      visitor.binaryField(fieldInfo, copy);
    } else if (type == SecureSimpleTextStoredFieldsWriter.TYPE_INT) {
      UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length, scratchUTF16);
      visitor.intField(fieldInfo, Integer.parseInt(scratchUTF16.toString()));
    } else if (type == SecureSimpleTextStoredFieldsWriter.TYPE_LONG) {
      UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length, scratchUTF16);
      visitor.longField(fieldInfo, Long.parseLong(scratchUTF16.toString()));
    } else if (type == SecureSimpleTextStoredFieldsWriter.TYPE_FLOAT) {
      UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length, scratchUTF16);
      visitor.floatField(fieldInfo, Float.parseFloat(scratchUTF16.toString()));
    } else if (type == SecureSimpleTextStoredFieldsWriter.TYPE_DOUBLE) {
      UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+ SecureSimpleTextStoredFieldsWriter.VALUE.length, scratch.length- SecureSimpleTextStoredFieldsWriter.VALUE.length, scratchUTF16);
      visitor.doubleField(fieldInfo, Double.parseDouble(scratchUTF16.toString()));
    }
  }

  @Override
  public StoredFieldsReader clone() {
    if (in == null) {
      throw new AlreadyClosedException("this FieldsReader is closed");
    }
    return new SecureSimpleTextStoredFieldsReader(offsets, in.clone(), fieldInfos);
  }
  
  @Override
  public void close() throws IOException {
    try {
      IOUtils.close(in); 
    } finally {
      in = null;
      offsets = null;
    }
  }
  
  private void readLine() throws IOException {
    SimpleTextUtil.readLine(in, scratch);
  }
  
  private int parseIntAt(int offset) {
    UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+offset, scratch.length-offset, scratchUTF16);
    return ArrayUtil.parseInt(scratchUTF16.chars, 0, scratchUTF16.length);
  }
  
  private boolean equalsAt(BytesRef a, BytesRef b, int bOffset) {
    return a.length == b.length - bOffset && 
        ArrayUtil.equals(a.bytes, a.offset, b.bytes, b.offset + bOffset, b.length - bOffset);
  }

  @Override
  public long ramBytesUsed() {
    return 0;
  }
}
