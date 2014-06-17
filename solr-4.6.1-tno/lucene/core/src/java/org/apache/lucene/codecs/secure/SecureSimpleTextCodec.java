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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.LiveDocsFormat;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.TermVectorsFormat;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * plain text index format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public final class SecureSimpleTextCodec extends SecureCodec {
  private final SecurePostingsFormat postings = new SecureSimpleTextPostingsFormat();
  private final SecureStoredFieldsFormat storedFields = new SecureSimpleTextStoredFieldsFormat();
//  private final SegmentInfoFormat segmentInfos = new SimpleTextSegmentInfoFormat();
//  private final FieldInfosFormat fieldInfosFormat = new SimpleTextFieldInfosFormat();
//  private final TermVectorsFormat vectorsFormat = new SimpleTextTermVectorsFormat();
//  private final NormsFormat normsFormat = new SimpleTextNormsFormat();
//  private final LiveDocsFormat liveDocs = new SimpleTextLiveDocsFormat();
//  private final DocValuesFormat dvFormat = new SimpleTextDocValuesFormat();

  public SecureSimpleTextCodec() {
    super("SecureSimpleText");
  }
  
  @Override
  public SecureStoredFieldsFormat secureStoredFieldsFormat() {
    return storedFields;
  }

  @Override
  public PostingsFormat postingsFormat() { return postings;}
}
