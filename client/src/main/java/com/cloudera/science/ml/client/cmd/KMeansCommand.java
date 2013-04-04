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
package com.cloudera.science.ml.client.cmd;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.apache.hadoop.conf.Configuration;
import org.apache.mahout.math.Vector;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.beust.jcommander.converters.CommaParameterSplitter;
import com.beust.jcommander.converters.IntegerConverter;
import com.cloudera.science.ml.avro.MLWeightedCenters;
import com.cloudera.science.ml.client.params.RandomParameters;
import com.cloudera.science.ml.client.util.AvroIO;
import com.cloudera.science.ml.core.vectors.Centers;
import com.cloudera.science.ml.core.vectors.VectorConvert;
import com.cloudera.science.ml.core.vectors.Weighted;
import com.cloudera.science.ml.kmeans.core.KMeans;
import com.cloudera.science.ml.kmeans.core.KMeansInitStrategy;
import com.cloudera.science.ml.kmeans.core.KMeansEvaluation;
import com.cloudera.science.ml.kmeans.core.StoppingCriteria;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

@Parameters(commandDescription = "Executes k-means++ on Avro vectors stored on the local filesystem")
public class KMeansCommand implements Command {

  @Parameter(names = "--input-file", required=true,
      description = "The local Avro file that contains the sketches computed by the ksketch command")
  private String sketchFile;

  @Parameter(names = "--clusters", required=true,
      description = "A CSV containing the number of clusters to create from the sample",
      splitter = CommaParameterSplitter.class,
      converter = IntegerConverter.class)
  private List<Integer> clusters = Lists.newArrayList();
  
  @Parameter(names = "--best-of",
      description = "Run this many iterations of k-means for each value of K")
  private int bestOf = 5;
  
  @Parameter(names = "--init-strategy",
      description = "The k-means initialization strategy (PLUS_PLUS or RANDOM)")
  private String initStrategyName = KMeansInitStrategy.PLUS_PLUS.name();

  @Parameter(names = "--max-iterations",
      description = "The maximum number of Lloyd's iterations to run")
  private int maxLloydsIterations = 100;  

  @Parameter(names = "--stopping-threshold",
      description = "Stop the Lloyd's iterations if the delta between centers falls below this")
  private double stoppingThreshold = 1.0e-4;  

  @Parameter(names = "--centers-file",
      description = "A local file to store the centers that were created into")
  private String centersOutputFile;
  
  @Parameter(names = "--num-threads",
      description = "The number of execution threads to use for running the (computationally intensive) k-means algorithm")
  private int numThreads = 1;
  
  @ParametersDelegate
  private RandomParameters randomParams = new RandomParameters();
  
  @Override
  public String getDescription() {
    return "Executes k-means++ on Avro vectors stored on the local filesystem";
  }
  
  @Override
  public int execute(Configuration conf) throws IOException {
    KMeansInitStrategy initStrategy = KMeansInitStrategy.valueOf(initStrategyName);
    KMeans kmeans = new KMeans(initStrategy, getStoppingCriteria());
    
    ListeningExecutorService exec;
    if (numThreads <= 1) {
      exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    } else {
      exec = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
    }
    
    List<MLWeightedCenters> mlwc = AvroIO.read(MLWeightedCenters.class, new File(sketchFile));
    List<List<Weighted<Vector>>> sketches = toSketches(mlwc);
    List<Weighted<Vector>> allPoints = Lists.newArrayList();
    for (List<Weighted<Vector>> sketch : sketches) {
      allPoints.addAll(sketch);
    }
    List<Centers> centers = getClusters(exec, allPoints, kmeans);
    AvroIO.write(Lists.transform(centers, VectorConvert.FROM_CENTERS),
        new File(centersOutputFile));
    
    if (sketches.size() > 1) {
      // Perform the prediction strength calculations on the folds
      List<Weighted<Vector>> train = Lists.newArrayList();
      for (int i = 0; i < sketches.size() - 1; i++) {
        train.addAll(sketches.get(i));
      }
      List<Weighted<Vector>> test = sketches.get(sketches.size() - 1);
      List<Centers> trainCenters = getClusters(exec, train, kmeans);
      List<Centers> testCenters = getClusters(exec, test, kmeans);
      KMeansEvaluation eval = new KMeansEvaluation(testCenters, test, trainCenters);
      System.out.println(
          "ID,NumClusters,TestCost,TrainCost,PredStrength,StableClusters,StablePoints");
      for (int i = 0; i < trainCenters.size(); i++) {
        System.out.println(String.format("%d,%d,%.2f,%.2f,%.4f,%.2f,%.4f",
            i, trainCenters.get(i).size(), eval.getTestCenterCosts().get(i),
            eval.getTrainCosts().get(i), eval.getPredictionStrengths().get(i),
            eval.getStableClusters().get(i), eval.getStablePoints().get(i)));
      }
    }
    
    return 0;
  }
  
  private List<Centers> getClusters(ListeningExecutorService exec,
      List<Weighted<Vector>> sketch,
      KMeans kmeans) {
    List<ListenableFuture<Centers>> futures = Lists.newArrayList();
    for (Integer nc : clusters) {
      int loops = nc == 1 ? 1 : bestOf;
      for (int i = 0; i < loops; i++) {
        Random r = randomParams.getRandom(nc + i);
        futures.add(exec.submit(new Clustering(kmeans, sketch, nc, r)));
      }
    }
    try {
      return Futures.allAsList(futures).get();
    } catch (Exception e) {
      throw new CommandException("Error in clustering", e);
    }
  }
  
  private static List<List<Weighted<Vector>>> toSketches(List<MLWeightedCenters> mlwc) {
    List<List<Weighted<Vector>>> base = Lists.newArrayList();
    for (MLWeightedCenters wc : mlwc) {
      base.add(Lists.transform(wc.getCenters(), VectorConvert.TO_WEIGHTED_VEC));
    }
    return base;
  }
  
  private StoppingCriteria getStoppingCriteria() {
    return StoppingCriteria.or(StoppingCriteria.threshold(stoppingThreshold),
        StoppingCriteria.maxIterations(maxLloydsIterations));
  }
  
  private static class Clustering implements Callable<Centers> {

    private final KMeans kmeans;
    private final List<Weighted<Vector>> sketch;
    private final int numClusters;
    private final Random r;
    
    Clustering(KMeans kmeans, List<Weighted<Vector>> sketch, int numClusters, Random r) {
      this.kmeans = kmeans;
      this.sketch = sketch;
      this.numClusters = numClusters;
      this.r = r;
    }

    @Override
    public Centers call() throws Exception {
      return kmeans.compute(sketch, numClusters, r);
    }
  }
}
