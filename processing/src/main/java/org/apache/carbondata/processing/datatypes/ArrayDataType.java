/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.carbondata.processing.datatypes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.keygenerator.KeyGenException;
import org.apache.carbondata.core.keygenerator.KeyGenerator;
import org.apache.carbondata.processing.surrogatekeysgenerator.csvbased.CarbonCSVBasedDimSurrogateKeyGen;

import org.pentaho.di.core.exception.KettleException;

/**
 * Array DataType stateless object used in data loading
 */
public class ArrayDataType implements GenericDataType {

  /**
   * child columns
   */
  private GenericDataType children;

  /**
   * name of the column
   */
  private String name;

  /**
   * column unique id
   */
  private String columnId;

  /**
   * parent column name
   */
  private String parentname;

  /**
   * output array index
   */
  private int outputArrayIndex;

  /**
   * current data counter
   */
  private int dataCounter;

  /**
   * constructor
   * @param name
   * @param parentname
   * @param columnId
   */
  public ArrayDataType(String name, String parentname, String columnId) {
    this.name = name;
    this.parentname = parentname;
    this.columnId = columnId;
  }

  /*
   * to add child dimensions
   */
  @Override
  public void addChildren(GenericDataType children) {
    if (this.getName().equals(children.getParentname())) {
      this.children = children;
    } else {
      this.children.addChildren(children);
    }
  }

  /*
   * return column name
   */
  @Override
  public String getName() {
    return name;
  }

  /*
   * return column unique id
   */
  @Override
  public String getColumnId() {
    return columnId;
  }

  /*
   * set column name
   */
  @Override
  public void setName(String name) {
    this.name = name;
  }

  /*
   * set parent name
   */
  @Override
  public String getParentname() {
    return parentname;
  }

  /*
   * returns all primitive type child columns
   */
  @Override
  public void getAllPrimitiveChildren(List<GenericDataType> primitiveChild) {
    if (children instanceof PrimitiveDataType) {
      primitiveChild.add(children);
    } else {
      children.getAllPrimitiveChildren(primitiveChild);
    }
  }

  /*
   * return surrogate index
   */
  @Override
  public int getSurrogateIndex() {
    return 0;
  }

  /*
   * set surrogate index
   */
  @Override
  public void setSurrogateIndex(int surrIndex) {

  }

  /*
   * parse string and generate surrogate
   */
  @Override
  public void parseStringAndWriteByteArray(String tableName, String inputString,
      String[] delimiter, int delimiterIndex, DataOutputStream dataOutputStream,
      CarbonCSVBasedDimSurrogateKeyGen surrogateKeyGen) throws KettleException, IOException {

    if (inputString == null || "null".equals(inputString) || "".equals(inputString) ||
        CarbonCommonConstants.MEMBER_DEFAULT_VAL.equals(inputString)) {
      dataOutputStream.writeInt(1);
      children.parseStringAndWriteByteArray(tableName,
          CarbonCommonConstants.MEMBER_DEFAULT_VAL, delimiter, delimiterIndex, dataOutputStream,
          surrogateKeyGen);
    } else {
      String[] splitInput = inputString.split(delimiter[delimiterIndex], -1);
      dataOutputStream.writeInt(splitInput.length);
      delimiterIndex =
          (delimiter.length - 1) == delimiterIndex ? delimiterIndex : delimiterIndex + 1;
      for (String eachInput : splitInput) {
        children.parseStringAndWriteByteArray(tableName, eachInput, delimiter, delimiterIndex,
            dataOutputStream, surrogateKeyGen);
      }
    }
  }

  /*
   * parse byte array and bit pack
   */
  @Override
  public void parseAndBitPack(ByteBuffer byteArrayInput, DataOutputStream dataOutputStream,
      KeyGenerator[] generator) throws IOException, KeyGenException {
    int dataLength = byteArrayInput.getInt();

    dataOutputStream.writeInt(dataLength);
    if (children instanceof PrimitiveDataType) {
      dataOutputStream.writeInt(generator[children.getSurrogateIndex()].getKeySizeInBytes());
    }
    for (int i = 0; i < dataLength; i++) {
      children.parseAndBitPack(byteArrayInput, dataOutputStream, generator);
    }

  }

  /*
   * get children column count
   */
  @Override
  public int getColsCount() {
    return children.getColsCount() + 1;
  }

  /*
   * set array index
   */
  @Override
  public void setOutputArrayIndex(int outputArrayIndex) {
    this.outputArrayIndex = outputArrayIndex;
    children.setOutputArrayIndex(outputArrayIndex + 1);
  }

  /*
   * get current max array index
   */
  @Override
  public int getMaxOutputArrayIndex() {
    int currentMax = outputArrayIndex;
    int childMax = children.getMaxOutputArrayIndex();
    if (childMax > currentMax) {
      currentMax = childMax;
    }
    return currentMax;
  }

  /*
   * split byte array and return metadata and primitive column data
   */
  @Override
  public void getColumnarDataForComplexType(List<ArrayList<byte[]>> columnsArray,
      ByteBuffer inputArray) {
    ByteBuffer b = ByteBuffer.allocate(8);
    int dataLength = inputArray.getInt();
    b.putInt(dataLength);
    if (dataLength == 0) {
      b.putInt(0);
    } else {
      b.putInt(children.getDataCounter());
    }
    columnsArray.get(this.outputArrayIndex).add(b.array());

    if (children instanceof PrimitiveDataType) {
      ((PrimitiveDataType) children).setKeySize(inputArray.getInt());
    }
    for (int i = 0; i < dataLength; i++) {
      children.getColumnarDataForComplexType(columnsArray, inputArray);
    }
    this.dataCounter++;
  }

  /*
   * return data counter
   */
  @Override
  public int getDataCounter() {
    return this.dataCounter;
  }

  /*
   * fill agg key blocks
   */
  @Override
  public void fillAggKeyBlock(List<Boolean> aggKeyBlockWithComplex, boolean[] aggKeyBlock) {
    aggKeyBlockWithComplex.add(false);
    children.fillAggKeyBlock(aggKeyBlockWithComplex, aggKeyBlock);
  }

  /*
   * fill key size
   */
  @Override
  public void fillBlockKeySize(List<Integer> blockKeySizeWithComplex, int[] primitiveBlockKeySize) {
    blockKeySizeWithComplex.add(8);
    children.fillBlockKeySize(blockKeySizeWithComplex, primitiveBlockKeySize);
  }

  /*
   * fill cardinality
   */
  @Override
  public void fillCardinalityAfterDataLoad(List<Integer> dimCardWithComplex,
      int[] maxSurrogateKeyArray) {
    dimCardWithComplex.add(0);
    children.fillCardinalityAfterDataLoad(dimCardWithComplex, maxSurrogateKeyArray);
  }

}
