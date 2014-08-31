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
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.LiveDocsFormat;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.lucene46.Lucene46Codec;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * plain text index format.
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public final class SecureCipherCodec extends SecureCodec {
  private final Codec luceneCodec;

  private final SecurePostingsFormat securePostingsFormat = new SecureCipherPostingsFormat();

  private final PostingsFormat postings = new PerFieldPostingsFormat() {
    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
      return SecureCipherCodec.this.getPostingsFormatForField(field);
    }
  };

  public PostingsFormat getPostingsFormatForField(String field) {
    if (ConfigurationUtil.isEncrypted(field)) {
      return securePostingsFormat;
    } else {
      PostingsFormat lucenePostings = luceneCodec.postingsFormat();
      if (lucenePostings instanceof PerFieldPostingsFormat) {
        return ((PerFieldPostingsFormat) lucenePostings).getPostingsFormatForField(field);
      } else {
        return lucenePostings;
      }
    }
  }

  public SecureCipherCodec() {
    super("SecureCipher");
    this.luceneCodec = new Lucene46Codec();
  }

  public SecureCipherCodec(Codec luceneCodec) {
    super("SecureCipher");
    this.luceneCodec = luceneCodec;
  }

//  private final SecurePostingsFormat postings = new SecureCipherPostingsFormat();
//  private final SecureStoredFieldsFormat storedFields = new SecureSimpleTextCipherStoredFieldsFormat();
//
//  @Override
//  public SecureStoredFieldsFormat secureStoredFieldsFormat() {
//    return storedFields;
//  }
//
//  @Override
//  public SecureStoredFieldsFormat secureStoredFieldsFormat(Codec codec) {
//    return new SecureCipherStoredFieldsFormat(codec);
//  }

  @Override
  /** Encodes/decodes stored fields */
  public final StoredFieldsFormat storedFieldsFormat() {
    return new SecureCipherStoredFieldsFormat(luceneCodec);
  }

  @Override
  public PostingsFormat postingsFormat() { return postings;}

  @Override
  /** Encodes/decodes docvalues */
  public final DocValuesFormat docValuesFormat() { return luceneCodec.docValuesFormat(); }

  @Override
  /** Encodes/decodes term vectors */
  public final TermVectorsFormat termVectorsFormat() { return luceneCodec.termVectorsFormat(); }

  @Override
  /** Encodes/decodes field infos file */
  public final FieldInfosFormat fieldInfosFormat() { return luceneCodec.fieldInfosFormat(); }

  @Override
  /** Encodes/decodes segment info file */
  public final SegmentInfoFormat segmentInfoFormat() { return luceneCodec.segmentInfoFormat(); }

  @Override
  /** Encodes/decodes document normalization values */
  public final NormsFormat normsFormat() { return luceneCodec.normsFormat(); }

  @Override
  /** Encodes/decodes live docs */
  public final LiveDocsFormat liveDocsFormat() { return luceneCodec.liveDocsFormat(); }
}
