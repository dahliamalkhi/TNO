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

import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexableField;

import java.io.IOException;

/**
 * Codec API for writing encrypted stored fields:
 * <p>
 * <ol>
 *   <li>For every document, {@link #startDocument(int)} is called,
 *       informing the Codec how many fields will be written.
 *   <li>{@link #writeField(org.apache.lucene.index.FieldInfo, org.apache.lucene.index.IndexableField)} is called for
 *       each field in the document.
 *   <li>After all documents have been written, {@link #finish(org.apache.lucene.index.FieldInfos, int)}
 *       is called for verification/sanity-checks.
 *   <li>Finally the writer is closed ({@link #close()})
 * </ol>
 *
 * @lucene.experimental
 */
public abstract class SecureStoredFieldsWriter extends StoredFieldsWriter {

  /** Sole constructor. (For invocation by subclass
   *  constructors, typically implicit.) */
  protected SecureStoredFieldsWriter() {
  }

  @Override
  /** Called before writing the encrypted stored fields of the document.
   *  {@link #writeField(org.apache.lucene.index.FieldInfo, org.apache.lucene.index.IndexableField)} will be called
   *  <code>numEncryptedStoredFields</code> times. Note that this is
   *  called even if the document has no encrypted stored fields, in
   *  this case <code>numEncryptedStoredFields</code> will be zero. */
  public abstract void startDocument(int numEncryptedStoredFields) throws IOException;

  @Override
  /** Called when a document and all its fields have been added. */
  public void finishDocument() throws IOException {}

  @Override
  /** Writes a single encrypted stored field. */
  public abstract void writeField(FieldInfo info, IndexableField field) throws IOException;

  /** Aborts writing entirely, implementation should remove
   *  any partially-written files, etc. */
  public abstract void abort();

  @Override
  /** Called before {@link #close()}, passing in the number
   *  of documents that were written. Note that this is
   *  intentionally redundant (equivalent to the number of
   *  calls to {@link #startDocument(int)}, but a Codec should
   *  check that this is the case to detect the JRE bug described
   *  in LUCENE-1282. */
  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;

  @Override
  public abstract void close() throws IOException;
}
