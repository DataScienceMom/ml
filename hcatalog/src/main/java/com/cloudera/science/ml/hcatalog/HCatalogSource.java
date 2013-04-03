/**
 * Copyright (c) 2013, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.science.ml.hcatalog;

import java.io.IOException;

import org.apache.crunch.Source;
import org.apache.crunch.types.PType;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;

import com.cloudera.science.ml.core.records.Record;

/**
 *
 */
public class HCatalogSource implements Source<Record> {

  @Override
  public void configureSource(Job job, int index) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public long getSize(Configuration conf) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public PType<Record> getType() {
    // TODO Auto-generated method stub
    return null;
  }

}
