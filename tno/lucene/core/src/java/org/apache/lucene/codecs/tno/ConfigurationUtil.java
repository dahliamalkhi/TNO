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

import java.util.ArrayList;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class ConfigurationUtil {
  //public static Logger log = LoggerFactory.getLogger(SecureCipherUtil.class);

  private static ArrayList<String> encryptedFields;

  /**
   * hard code master key and make keys always available
   * until we write a proper key management service
   */
  static {
    encryptedFields = new ArrayList<String>(3);
    encryptedFields.add("MG".toLowerCase());
    encryptedFields.add("manu".toLowerCase());
    encryptedFields.add("manu_id_s".toLowerCase());
  }

  public static boolean isEncrypted(String field)
  {
    return encryptedFields.contains(field.toLowerCase());
  }
}