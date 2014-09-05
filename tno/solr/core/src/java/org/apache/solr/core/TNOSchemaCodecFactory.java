package org.apache.solr.core;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene46.Lucene46Codec;
import org.apache.lucene.codecs.tno.SecureCipherCodec;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.util.plugin.SolrCoreAware;

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

/**
 * TNO CodecFactory implementation, extends Solr's SchemaCodecFactory
 * and returns secure cipher codec wrapped around Solr's codec.
 */
public class TNOSchemaCodecFactory extends SchemaCodecFactory {
  private Codec secureCodec;

  @Override
  public void init(NamedList args) {
    super.init(args);
    secureCodec = new SecureCipherCodec(super.getCodec());
  }

  @Override
  public Codec getCodec() {
    return secureCodec;
  }
}
