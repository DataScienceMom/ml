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
package com.cloudera.science.ml.classifier.core;

import java.io.Serializable;

/**
 *
 */
public interface EtaUpdate extends Serializable {
  public double compute(double lambda, int numObservations);
  
  public static class ConstantEtaUpdate implements EtaUpdate {
    @Override
    public double compute(double lambda, int numObservations) {
      return 0.02;
    }
  };
  
  public static final EtaUpdate CONSTANT = new ConstantEtaUpdate();
  
  public static class BasicEtaUpdate implements EtaUpdate {
    @Override
    public double compute(double lambda, int numObservations) {
      return  10.0 / (numObservations + 10.0);
    }
  };
  
  public static final EtaUpdate BASIC_ETA = new BasicEtaUpdate();
  
  public static class PegasosEtaUpdate implements EtaUpdate {
    @Override
    public double compute(double lambda, int numObservations) {
      return  1.0 / (lambda * numObservations);
    }
  };
  
  public static final EtaUpdate PEGASOS_ETA = new PegasosEtaUpdate();
}
