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

package org.apache.carbondata.processing.newflow;

import java.util.Iterator;

import org.apache.carbondata.common.CarbonIterator;
import org.apache.carbondata.processing.newflow.exception.CarbonDataLoadingException;
import org.apache.carbondata.processing.newflow.row.CarbonRow;
import org.apache.carbondata.processing.newflow.row.CarbonRowBatch;

/**
 * This base abstract class for data loading.
 * It can do transformation jobs as per the implementation.
 */
public abstract class AbstractDataLoadProcessorStep {

  protected CarbonDataLoadConfiguration configuration;

  protected AbstractDataLoadProcessorStep child;

  public AbstractDataLoadProcessorStep(CarbonDataLoadConfiguration configuration,
      AbstractDataLoadProcessorStep child) {
    this.configuration = configuration;
    this.child = child;
  }

  /**
   * The output meta for this step. The data returns from this step is as per this meta.
   *
   */
  public abstract DataField[] getOutput();

  /**
   * Intialization process for this step.
   *
   * @throws CarbonDataLoadingException
   */
  public abstract void intialize() throws CarbonDataLoadingException;

  /**
   * Tranform the data as per the implementation.
   *
   * @return Array of Iterator with data. It can be processed parallel if implementation class wants
   * @throws CarbonDataLoadingException
   */
  public Iterator<CarbonRowBatch>[] execute() throws CarbonDataLoadingException {
    Iterator<CarbonRowBatch>[] childIters = child.execute();
    Iterator<CarbonRowBatch>[] iterators = new Iterator[childIters.length];
    for (int i = 0; i < childIters.length; i++) {
      iterators[i] = getIterator(childIters[i]);
    }
    return iterators;
  }

  /**
   * Create the iterator using child iterator.
   *
   * @param childIter
   * @return new iterator with step specific processing.
   */
  protected Iterator<CarbonRowBatch> getIterator(final Iterator<CarbonRowBatch> childIter) {
    return new CarbonIterator<CarbonRowBatch>() {
      @Override public boolean hasNext() {
        return childIter.hasNext();
      }

      @Override public CarbonRowBatch next() {
        return processRowBatch(childIter.next());
      }
    };
  }

  /**
   * Process the batch of rows as per the step logic.
   *
   * @param rowBatch
   * @return processed row.
   */
  protected CarbonRowBatch processRowBatch(CarbonRowBatch rowBatch) {
    CarbonRowBatch newBatch = new CarbonRowBatch();
    Iterator<CarbonRow> batchIterator = rowBatch.getBatchIterator();
    while (batchIterator.hasNext()) {
      newBatch.addRow(processRow(batchIterator.next()));
    }
    return newBatch;
  }

  /**
   * Process the row as per the step logic.
   *
   * @param row
   * @return processed row.
   */
  protected abstract CarbonRow processRow(CarbonRow row);

  /**
   * It is called when task is called successfully.
   */
  public abstract void finish();

  /**
   * Closing of resources after step execution can be done here.
   */
  public abstract void close();

}
