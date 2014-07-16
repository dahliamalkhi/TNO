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

import javax.crypto.Cipher;
import java.io.IOException;

import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.DOC;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.END;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.FIELD;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.NAME;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.NUM;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_BINARY;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_DOUBLE;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_FLOAT;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_INT;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_LONG;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.TYPE_STRING;
import static org.apache.lucene.codecs.secure.SecureSimpleTextStoredFieldsWriter.VALUE;

//import  org.apache.lucene.codecs.simpletext.SimpleTextUtil;

/**
 * reads plaintext encrypted stored fields
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SecureSimpleTextCipherStoredFieldsReader extends SecureStoredFieldsReader {
  private long offsets[]; /* docid -> offset in .sec.fld file */
  private IndexInput in;
  private BytesRef scratch = new BytesRef();
  private CharsRef scratchUTF16 = new CharsRef();
  private final FieldInfos fieldInfos;
  private Cipher cipher = null;


  public SecureSimpleTextCipherStoredFieldsReader(Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    this.fieldInfos = fn;
    boolean success = false;
    try {
      in = directory.openInput(IndexFileNames.segmentFileName(si.name, "", SecureSimpleTextStoredFieldsWriter.FIELDS_EXTENSION), context);
      cipher = Cipher.getInstance("AES");
      success = true;
    }  catch (Exception ex) {} finally {
      if (!success) {
        try {
          close();
        } catch (Throwable t) {} // ensure we throw our original exception
      }
    }
    readIndex(si.getDocCount());
  }

  // used by clone
  SecureSimpleTextCipherStoredFieldsReader(long offsets[], IndexInput in, FieldInfos fieldInfos) {
    this.offsets = offsets;
    this.in = in;
    this.fieldInfos = fieldInfos;
    try {
      this.cipher = Cipher.getInstance("AES");
    } catch (Exception ex) {
      throw new Error(ex);
    }
  }
  
  // we don't actually write a .fdx-like index, instead we read the 
  // stored fields file in entirety up-front and save the offsets 
  // so we can seek to the documents later.
  private void readIndex(int size) throws IOException {
    offsets = new long[size];
    int upto = 0;
    while (!scratch.equals(END)) {
      readLine();
      if (StringHelper.startsWith(scratch, DOC)) {
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
    assert StringHelper.startsWith(scratch, NUM);
    int numFields = parseIntAt(NUM.length);
    
    for (int i = 0; i < numFields; i++) {
      readLine();
      assert StringHelper.startsWith(scratch, FIELD);
      int fieldNumber = parseIntAt(FIELD.length);
      FieldInfo fieldInfo = fieldInfos.fieldInfo(fieldNumber);
      assert fieldInfo.isEncrypted();
      readLine();
      assert StringHelper.startsWith(scratch, NAME);
      readLine();
      assert StringHelper.startsWith(scratch, TYPE);
      
      final BytesRef type;
      if (equalsAt(TYPE_STRING, scratch, TYPE.length)) {
        type = TYPE_STRING;
      } else if (equalsAt(TYPE_BINARY, scratch, TYPE.length)) {
        type = TYPE_BINARY;
      } else if (equalsAt(TYPE_INT, scratch, TYPE.length)) {
        type = TYPE_INT;
      } else if (equalsAt(TYPE_LONG, scratch, TYPE.length)) {
        type = TYPE_LONG;
      } else if (equalsAt(TYPE_FLOAT, scratch, TYPE.length)) {
        type = TYPE_FLOAT;
      } else if (equalsAt(TYPE_DOUBLE, scratch, TYPE.length)) {
        type = TYPE_DOUBLE;
      } else {
        throw new RuntimeException("unknown field type");
      }

      switch (visitor.needsField(fieldInfo)) {
        case YES:
          readField(type, fieldInfo, visitor);
          break;
        case NO:   
          readLine();
          assert StringHelper.startsWith(scratch, VALUE);
          break;
        case STOP: return;
      }
    }
  }
  
  private void readField(BytesRef type, FieldInfo fieldInfo, StoredFieldVisitor visitor) throws IOException {
    readLine();
    assert StringHelper.startsWith(scratch, VALUE);

    String value = new String(scratch.bytes, scratch.offset + VALUE.length, scratch.length - VALUE.length, "UTF-8");
    if (!SecureCipherUtil.hasKey(fieldInfo.name)) {
      if (type == TYPE_BINARY) {
        byte[] bytes = SecureCipherUtil.decode(value);
        visitor.binaryField(fieldInfo, bytes);
      } else {
        visitor.stringField(fieldInfo, value);
      }
      return;
    }

    byte[] bytes = SecureCipherUtil.decode(value);
    byte[] decryptedBytes = null;
    try {
      cipher.init(Cipher.DECRYPT_MODE, SecureCipherUtil.getKey(fieldInfo.name));
      decryptedBytes = cipher.doFinal(bytes);
    } catch (Exception ex) {
      if (type == TYPE_BINARY) {
        visitor.binaryField(fieldInfo, bytes);
      } else {
        visitor.stringField(fieldInfo, value);
      }
      return;
    }

    assert decryptedBytes != null;
    if (type == TYPE_BINARY) {
      visitor.binaryField(fieldInfo, decryptedBytes);
      //byte[] copy = new byte[scratch.length-VALUE.length];
      //System.arraycopy(scratch.bytes, scratch.offset+VALUE.length, copy, 0, copy.length);
      //visitor.binaryField(fieldInfo, copy);
    } else {
      value = new String(decryptedBytes);
      if (type == TYPE_STRING) {
        visitor.stringField(fieldInfo, value);
        //visitor.stringField(fieldInfo, new String(scratch.bytes, scratch.offset+VALUE.length, scratch.length-VALUE.length, "UTF-8"));
      } else if (type == TYPE_INT) {
        //UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+VALUE.length, scratch.length-VALUE.length, scratchUTF16);
        //visitor.intField(fieldInfo, Integer.parseInt(scratchUTF16.toString()));
        visitor.intField(fieldInfo, Integer.parseInt(value));
      } else if (type == TYPE_LONG) {
        //UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+VALUE.length, scratch.length-VALUE.length, scratchUTF16);
        //visitor.longField(fieldInfo, Long.parseLong(scratchUTF16.toString()));
        visitor.longField(fieldInfo, Long.parseLong(value));
      } else if (type == TYPE_FLOAT) {
        //UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+VALUE.length, scratch.length-VALUE.length, scratchUTF16);
        //visitor.floatField(fieldInfo, Float.parseFloat(scratchUTF16.toString()));
        visitor.floatField(fieldInfo, Float.parseFloat(value));
      } else if (type == TYPE_DOUBLE) {
        //UnicodeUtil.UTF8toUTF16(scratch.bytes, scratch.offset+VALUE.length, scratch.length-VALUE.length, scratchUTF16);
        //visitor.doubleField(fieldInfo, Double.parseDouble(scratchUTF16.toString()));
        visitor.doubleField(fieldInfo, Double.parseDouble(value));
      }
    }
  }

  @Override
  public StoredFieldsReader clone() {
    if (in == null) {
      throw new AlreadyClosedException("this FieldsReader is closed");
    }
    return new SecureSimpleTextCipherStoredFieldsReader(offsets, in.clone(), fieldInfos);
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
