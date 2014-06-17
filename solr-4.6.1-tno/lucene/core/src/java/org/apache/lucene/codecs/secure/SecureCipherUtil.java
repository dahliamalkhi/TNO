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


import org.apache.lucene.util.BytesRef;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.SecureRandom;

public class SecureCipherUtil {
  //public static Logger log = LoggerFactory.getLogger(SecureCipherUtil.class);

  private static final String EncryptionAlgorithm = "AES";
  private static final String KeyDerivationAlgorithm = "HmacSHA256";

  private static Mac mac;
  private static KeyGenerator kg;

  private static HashMap<BytesRef,SecretKey> keys;
  private static SecretKey key;

  private static final String FileName = "SecureCipherUtil.Keys.txt";
  private static PrintStream out;
  private static boolean fileKeys = true;

  public static SecretKey generateKey() {
    try {
      SecretKey key = null;
      if (key == null) {
        if (kg == null) kg = KeyGenerator.getInstance(EncryptionAlgorithm);
        kg.init(new SecureRandom());
        key = kg.generateKey();
        if (fileKeys) writeKey(null, key);
        System.out.println();
        System.out.println("DBG: SECURECIPHERUTIL: generated collection key");
        System.out.println();
      }
      return key;
    }  catch (Exception ex) {
      throw new Error(ex.toString());
    }
  }

  public static SecretKey deriveKey(SecretKey masterKey, String field) {
    SecretKey key = deriveKey(masterKey, field.getBytes());
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: derived key for field " + field);
    System.out.println();
    return key;
  }

  public static SecretKey deriveKey(SecretKey masterKey, String field, BytesRef term) {
    SecretKey key = deriveKey(masterKey, getFieldAndTerm(field, term));
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: derived key for term " + field + ":" + new String(term.bytes, term.offset, term.length));
    System.out.println();
    return key;
  }

  private static byte[] getFieldAndTerm(String field, BytesRef term) {
    byte[] fieldBytes = field.getBytes();
    byte[] fieldandterm = new byte[fieldBytes.length + term.length];
    System.arraycopy(fieldBytes, 0, fieldandterm, 0, fieldBytes.length);
    System.arraycopy(term.bytes, term.offset, fieldandterm, fieldBytes.length, term.length);
    return fieldandterm;
  }

  private static SecretKey deriveKey(SecretKey masterKey, byte[] identity) {
    try {
      if (mac == null) mac = Mac.getInstance(KeyDerivationAlgorithm);
      mac.init(new SecretKeySpec(masterKey.getEncoded(), KeyDerivationAlgorithm));
      SecretKeySpec derivedKey = new SecretKeySpec(mac.doFinal(identity), 0, 128/8, EncryptionAlgorithm);
      addKey(new BytesRef(identity), derivedKey);
      if (fileKeys) writeKey(identity, derivedKey);
      return derivedKey;
    }  catch (Exception ex) {
      throw new Error(ex.toString());
    }
  }

  public static void addKey(SecretKey key) {
    SecureCipherUtil.key = key;
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: added collection key");
    System.out.println();
  }

  public static void addKey(String field, SecretKey key)
  {
    addKey(new BytesRef(field.getBytes()), key);
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: added key for field " + field);
    System.out.println();
  }

  public static void addKey(String field, BytesRef term, SecretKey key)
  {
    addKey(new BytesRef(getFieldAndTerm(field, term)), key);
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: added key for term " + field + ":" + new String(term.bytes, term.offset, term.length));
    System.out.println();
  }

  private static void addKey(BytesRef identity, SecretKey key) {
    if (keys == null) keys = new HashMap<BytesRef,SecretKey>();
    keys.put(identity,key);
  }

  public static void deleteKey() {
    SecureCipherUtil.key = null;
    SecureCipherUtil.keys = null;
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: deleted all keys");
    System.out.println();
  }

  public static void deleteKey(String field)
  {
    BytesRef fieldBR = new BytesRef(field.getBytes());
    if (keys != null && keys.containsKey(fieldBR))
      keys.remove(fieldBR);
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: deleted key for field " + field);
    System.out.println();
  }

  public static void deleteKey(String field, BytesRef term)
  {
    BytesRef fieldandterm = new BytesRef(getFieldAndTerm(field, term));
    if (keys != null && keys.containsKey(fieldandterm))
      keys.remove(fieldandterm);
    System.out.println();
    System.out.println("DBG: SECURECIPHERUTIL: deleted key for term " + field + ":" + new String(term.bytes, term.offset, term.length));
    System.out.println();
  }

  public static boolean hasKey() {
    return key != null;
  }

  public static boolean hasKey(String field)
  {
    BytesRef fieldBR = new BytesRef(field.getBytes());
    return hasKey() || keys != null && keys.containsKey(fieldBR);
  }

  public static boolean hasKey(String field, BytesRef term)
  {
    BytesRef fieldandterm = new BytesRef(getFieldAndTerm(field, term));
    return hasKey() || keys != null && keys.containsKey(fieldandterm);
  }

  public static SecretKey getKey() {
    return SecureCipherUtil.key;
  }

  public static SecretKey getKey(String field)  { return getKey(new BytesRef(field.getBytes())); }

  public static SecretKey getKey(String field, BytesRef term) { return getKey(new BytesRef(getFieldAndTerm(field, term))); }

  private static SecretKey getKey(BytesRef identity) {
    if (keys != null && keys.containsKey(identity)) {
      return keys.get(identity);
    } else if (hasKey()) {
      return deriveKey(key, identity.bytes);
    } else {
      return null;
    }
  }

  public static SecretKey readKey() throws IOException {
    InputStream in = new FileInputStream(FileName);
    byte[] inputBytes = new byte[32];
    in.read();
    in.read(inputBytes, 0, inputBytes.length);
    byte[] byteArray = decode(new String(inputBytes));
    SecretKeySpec spec = new SecretKeySpec(byteArray, "AES");
//    in.readBytes(inputBytes, 0, inputBytes.length);
//    inputBytes = new byte[2];
//    in.readBytes(inputBytes, 0, inputBytes.length);
    return spec;
  }

  public static void writeKey(byte[] identity, SecretKey key) throws IOException {

    if (out == null) out = new PrintStream(FileName);
    if (identity != null) out.write(identity, 0, identity.length);
    out.print(':');
    byte[] byteArray = key.getEncoded();
    byte[] outputBytes = encode(byteArray).getBytes();
    out.write(outputBytes, 0, outputBytes.length);
    out.println();
    out.flush();
//    out.writeBytes(outputBytes, 0, outputBytes.length);
//    outputBytes = "\r\n".getBytes();
//    out.writeBytes(outputBytes, 0, outputBytes.length);
  }

  public static SecretKey readKey(String keyString) throws Exception {
    byte[] byteArray = decode(keyString);
    if (byteArray.length != 128/8) throw new InvalidKeyException();
    SecretKeySpec spec = new SecretKeySpec(byteArray, "AES");
    return spec;
  }

  private final static char[] HEX = new char[]{
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

  /**
   * Convert bytes to a base16 string.
   */
  public static String encode(byte[] byteArray) {
    StringBuffer hexBuffer = new StringBuffer(byteArray.length * 2);
    for (int i = 0; i < byteArray.length; i++)
      for (int j = 1; j >= 0; j--)
        hexBuffer.append(HEX[(byteArray[i] >> (j * 4)) & 0xF]);
    return hexBuffer.toString();
  }

  /**
   * Convert a base16 string into a byte array.
   */
  public static byte[] decode(String s) {
    int len = s.length();
    byte[] r = new byte[len / 2];
    for (int i = 0; i < r.length; i++) {
      int digit1 = s.charAt(i * 2), digit2 = s.charAt(i * 2 + 1);
      if (digit1 >= '0' && digit1 <= '9')
        digit1 -= '0';
      else if (digit1 >= 'A' && digit1 <= 'F')
        digit1 -= 'A' - 10;
      if (digit2 >= '0' && digit2 <= '9')
        digit2 -= '0';
      else if (digit2 >= 'A' && digit2 <= 'F')
        digit2 -= 'A' - 10;

      r[i] = (byte) ((digit1 << 4) + digit2);
    }
    return r;
  }
}