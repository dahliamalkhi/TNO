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

import org.apache.lucene.index.StoredFieldVisitor;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsReader;
/**
 * Codec API for reading encrypted stored fields.
 * <p>
 * You need to implement {@link #visitDocument(int, org.apache.lucene.index.StoredFieldVisitor)} to
 * read the encrypted stored fields for a document, implement {@link #clone()} (creating
 * clones of any IndexInputs used, etc), and {@link #close()}
 * @lucene.experimental
 */
public abstract class SecureStoredFieldsReader extends StoredFieldsReader {
  /** Sole constructor. (For invocation by subclass
   *  constructors, typically implicit.) */
  protected SecureStoredFieldsReader() {
  }

  @Override
  /** Visit the encrypted stored fields for document <code>n</code> */
  public abstract void visitDocument(int n, StoredFieldVisitor visitor) throws IOException;

  @Override
  public abstract StoredFieldsReader clone();

  @Override
  /** Returns approximate RAM bytes used */
  public abstract long ramBytesUsed();
}
