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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.Objects;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PointsReader;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.TermVectorsReader;
import org.apache.lucene.search.TopDocs;

/** LeafReader implemented by codec APIs. */
public abstract class CodecReader extends LeafReader {

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  protected CodecReader() {}

  /**
   * Expert: retrieve thread-private StoredFieldsReader
   *
   * @lucene.internal
   */
  public abstract StoredFieldsReader getFieldsReader();

  /**
   * Expert: retrieve TermVectorsReader
   *
   * @lucene.internal
   */
  @Override
  public abstract TermVectorsReader getTermVectorsReader();

  /**
   * Expert: retrieve underlying NormsProducer
   *
   * @lucene.internal
   */
  public abstract NormsProducer getNormsReader();

  /**
   * Expert: retrieve underlying DocValuesProducer
   *
   * @lucene.internal
   */
  public abstract DocValuesProducer getDocValuesReader();

  /**
   * Expert: retrieve underlying FieldsProducer
   *
   * @lucene.internal
   */
  public abstract FieldsProducer getPostingsReader();

  /**
   * Expert: retrieve underlying PointsReader
   *
   * @lucene.internal
   */
  public abstract PointsReader getPointsReader();

  /**
   * Expert: retrieve underlying VectorReader
   *
   * @lucene.internal
   */
  public abstract KnnVectorsReader getVectorReader();

  @Override
  public final void document(int docID, StoredFieldVisitor visitor) throws IOException {
    checkBounds(docID);
    getFieldsReader().visitDocument(docID, visitor);
  }

  private void checkBounds(int docID) {
    Objects.checkIndex(docID, maxDoc());
  }

  @Override
  public final Terms terms(String field) throws IOException {
    // ensureOpen(); no; getPostingsReader calls this
    // We could check the FieldInfo IndexOptions but there's no point since
    //   PostingsReader will simply return null for fields that don't exist or that have no terms
    // index.
    return getPostingsReader().terms(field);
  }

  // returns the FieldInfo that corresponds to the given field and type, or
  // null if the field does not exist, or not indexed as the requested
  // DovDocValuesType.
  private FieldInfo getDVField(String field, DocValuesType type) {
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null) {
      // Field does not exist
      return null;
    }
    if (fi.getDocValuesType() == DocValuesType.NONE) {
      // Field was not indexed with doc values
      return null;
    }
    if (fi.getDocValuesType() != type) {
      // Field DocValues are different than requested type
      return null;
    }

    return fi;
  }

  @Override
  public final NumericDocValues getNumericDocValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getDVField(field, DocValuesType.NUMERIC);
    if (fi == null) {
      return null;
    }
    return getDocValuesReader().getNumeric(fi);
  }

  @Override
  public final BinaryDocValues getBinaryDocValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getDVField(field, DocValuesType.BINARY);
    if (fi == null) {
      return null;
    }
    return getDocValuesReader().getBinary(fi);
  }

  @Override
  public final SortedDocValues getSortedDocValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getDVField(field, DocValuesType.SORTED);
    if (fi == null) {
      return null;
    }
    return getDocValuesReader().getSorted(fi);
  }

  @Override
  public final SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
    ensureOpen();

    FieldInfo fi = getDVField(field, DocValuesType.SORTED_NUMERIC);
    if (fi == null) {
      return null;
    }
    return getDocValuesReader().getSortedNumeric(fi);
  }

  @Override
  public final SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getDVField(field, DocValuesType.SORTED_SET);
    if (fi == null) {
      return null;
    }
    return getDocValuesReader().getSortedSet(fi);
  }

  @Override
  public final NumericDocValues getNormValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.hasNorms() == false) {
      // Field does not exist or does not index norms
      return null;
    }

    return getNormsReader().getNorms(fi);
  }

  @Override
  public final PointValues getPointValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.getPointDimensionCount() == 0) {
      // Field does not exist or does not index points
      return null;
    }

    return getPointsReader().getValues(field);
  }

  @Override
  public final VectorValues getVectorValues(String field) throws IOException {
    ensureOpen();
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.getVectorDimension() == 0) {
      // Field does not exist or does not index vectors
      return null;
    }

    return getVectorReader().getVectorValues(field);
  }

  @Override
  public final TopDocs searchNearestVectors(String field, float[] target, int k)
      throws IOException {
    ensureOpen();
    FieldInfo fi = getFieldInfos().fieldInfo(field);
    if (fi == null || fi.getVectorDimension() == 0) {
      // Field does not exist or does not index vectors
      return null;
    }

    return getVectorReader().search(field, target, k);
  }

  @Override
  protected void doClose() throws IOException {}

  @Override
  public void checkIntegrity() throws IOException {
    ensureOpen();

    // terms/postings
    getPostingsReader().checkIntegrity();

    // norms
    if (getNormsReader() != null) {
      getNormsReader().checkIntegrity();
    }

    // docvalues
    if (getDocValuesReader() != null) {
      getDocValuesReader().checkIntegrity();
    }

    // stored fields
    if (getFieldsReader() != null) {
      getFieldsReader().checkIntegrity();
    }

    // term vectors
    if (getTermVectorsReader() != null) {
      getTermVectorsReader().checkIntegrity();
    }

    // points
    if (getPointsReader() != null) {
      getPointsReader().checkIntegrity();
    }

    // vectors
    if (getVectorReader() != null) {
      getVectorReader().checkIntegrity();
    }
  }
}
