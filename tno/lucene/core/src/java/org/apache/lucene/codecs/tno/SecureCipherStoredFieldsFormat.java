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
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

import java.io.IOException;

/**
 * plain text stored fields format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
//public class SecureCipherStoredFieldsFormat extends SecureStoredFieldsFormat {
public class SecureCipherStoredFieldsFormat extends StoredFieldsFormat {
  public static final String EncryptionAlgorithm = "AES/CBC/PKCS5Padding";

  public static final int TYPE_STRING = 0x01;
  public static final int TYPE_BINARY = 0x02;
  public static final int TYPE_INT = 0x03;
  public static final int TYPE_FLOAT = 0x04;
  public static final int TYPE_LONG = 0x05;
  public static final int TYPE_DOUBLE = 0x06;

  private Codec codec;
  public SecureCipherStoredFieldsFormat(Codec codec) { this.codec = codec;}

  @Override
  public StoredFieldsReader fieldsReader(Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {;
    return new SecureCipherStoredFieldsReader(codec, directory, si, fn, context);
  }

  @Override
  public StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo si, IOContext context) throws IOException {
    return new SecureCipherStoredFieldsWriter(codec, directory, si, context);
  }
}
