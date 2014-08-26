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

import org.apache.lucene.codecs.BlockTreeTermsReader;
import org.apache.lucene.codecs.BlockTreeTermsWriter;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsReaderBase;
import org.apache.lucene.codecs.PostingsWriterBase;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsReader;
import org.apache.lucene.codecs.lucene41.Lucene41PostingsWriter;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.util.IOUtils;

import java.io.IOException;

/** For debugging, curiosity, transparency only!!  Do not
 *  use this codec in production.
 *
 *  <p>This codec stores all postings data in a single
 *  human-readable text file (_N.pst).  You can view this in
 *  any text editor, and even edit it to alter your index.
 *
 *  @lucene.experimental */
public final class SecureCipherPostingsFormat extends SecurePostingsFormat {

  public SecureCipherPostingsFormat() {
    super("SecureCipherPostingsFormat");
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    //return new SecureCipherFieldsWriter(state);

    PostingsWriterBase postingsWriter = new SecureCipherPostingsWriter(state);
    boolean success = false;
    try {
      FieldsConsumer ret = new BlockTreeTermsWriter(state,
          postingsWriter,
          BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE,
          BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsWriter);
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    //return new SecureCipherFieldsReader(state);

    PostingsReaderBase postingsReader = new SecureCipherPostingsReader(state.directory,
        state.fieldInfos,
        state.segmentInfo,
        state.context,
        state.segmentSuffix);
    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(state.directory,
          state.fieldInfos,
          state.segmentInfo,
          postingsReader,
          state.context,
          state.segmentSuffix,
          state.termsIndexDivisor);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsReader);
      }
    }
  }

  /** Extension of freq postings file */
  static final String POSTINGS_EXTENSION = "pst.enc";

  static String getPostingsFileName(String segment, String segmentSuffix) {
    return IndexFileNames.segmentFileName(segment, segmentSuffix, POSTINGS_EXTENSION);
  }
}
