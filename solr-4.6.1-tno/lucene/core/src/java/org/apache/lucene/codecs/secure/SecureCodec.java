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
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.util.NamedSPILoader;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Set;

/**
 * Encodes/decodes an encrypted inverted index segment.
 * <p>
 * Note, when extending this class, the name ({@link #getName}) is 
 * written into the index. In order for the segment to be read, the
 * name must resolve to your implementation via {@link #forName(String)}.
 * This method uses Java's 
 * {@link java.util.ServiceLoader Service Provider Interface} (SPI) to resolve codec names.
 * <p>
 * If you implement your own codec, make sure that it has a no-arg constructor
 * so SPI can load it.
 * @see java.util.ServiceLoader
 */
public abstract class SecureCodec extends Codec {
  /**
   * Creates a new secure codec.
   * <p>
   * The provided name will be written into the index segment: in order to
   * for the segment to be read this class should be registered with Java's
   * SPI mechanism (registered in META-INF/ of your jar file, etc).
   * @param name must be all ascii alphanumeric, and less than 128 characters in length.
   */
  protected SecureCodec(String name) {
    super(name);
  }

  /** Securely Encodes/decodes stored fields */
  public abstract SecureStoredFieldsFormat secureStoredFieldsFormat();

  @Override
  /** Encodes/decodes postings */
  public PostingsFormat postingsFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes docvalues */
  public final DocValuesFormat docValuesFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes stored fields */
  public final StoredFieldsFormat storedFieldsFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes term vectors */
  public final TermVectorsFormat termVectorsFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes field infos file */
  public final FieldInfosFormat fieldInfosFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes segment info file */
  public final SegmentInfoFormat segmentInfoFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes document normalization values */
  public final NormsFormat normsFormat() { throw new NotImplementedException(); }

  @Override
  /** Encodes/decodes live docs */
  public final LiveDocsFormat liveDocsFormat() { throw new NotImplementedException(); }

  //private static SecureCodec defaultCodec = new SecureSimpleTextCodec();
  private static SecureCodec defaultCodec = new SecureCipherCodec();

  /** expert: returns the default secure codec.
   */
  // TODO: should we use this, or maybe a system property is better?
  public static SecureCodec getDefault() {
    return defaultCodec;
  }

  /** expert: sets the default secure codec.
   */
  public static void setDefault(SecureCodec codec) {
    defaultCodec = codec;
  }
}
