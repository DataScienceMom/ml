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

import java.io.IOException;
import java.util.Locale;

import org.apache.crunch.PCollection;
import org.apache.crunch.Pipeline;
import org.apache.crunch.PipelineResult;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.types.PType;
import org.apache.hadoop.conf.Configuration;
import org.apache.mahout.math.Vector;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.cloudera.science.ml.client.params.OutputParameters;
import com.cloudera.science.ml.client.params.RecordInputParameters;
import com.cloudera.science.ml.client.params.SummaryParameters;
import com.cloudera.science.ml.core.records.Header;
import com.cloudera.science.ml.core.records.RecordSpec;
import com.cloudera.science.ml.core.records.Spec;
import com.cloudera.science.ml.core.records.Specs;
import com.cloudera.science.ml.parallel.normalize.Normalizer;
import com.cloudera.science.ml.parallel.normalize.Transform;
import com.cloudera.science.ml.parallel.records.Records;
import com.cloudera.science.ml.parallel.summary.Summary;

@Parameters(commandDescription = "Prepare input (CSV or Vectors) for k-means runs")
public class NormalizeCommand implements Command {

  @Parameter(names = "--id-column",
      description = "For CSV inputs, the name/index of the column of the file that contains the identifier for the record")
  private String idColumn = "-1";
  
  @Parameter(names = "--sparse",
      description = "Write the output in a sparse vector format")
  private Boolean sparse;
  
  @Parameter(names = "--summary-file",
      description = "The name of a local JSON file that contains the summary info to use for normalizing the data")
  private String summaryFile;
  
  @Parameter(names = "--output-path", required = true,
      description = "The name of the output path, which will overwrite any existing files with that name")
  private String outputFile;
  
  @Parameter(names = "--transform",
      description = "A transform to apply to the continuous columns: either NONE, Z, or LINEAR")
  private String transform = "NONE";

  @ParametersDelegate
  private RecordInputParameters inputParams = new RecordInputParameters();

  @ParametersDelegate
  private OutputParameters outputParams = new OutputParameters();
  
  @ParametersDelegate
  private SummaryParameters summaryParams = new SummaryParameters();
  
  @Override
  public String getDescription() {
    return "Prepare input (CSV or Vectors) for k-means";
  }
  
  @Override
  public int execute(Configuration conf) throws IOException {
    Pipeline p = new MRPipeline(NormalizeCommand.class, conf);
    
    Summary summary = null;
    RecordSpec spec = null;
    Header header = null;
    if (summaryFile != null) {
      summary = summaryParams.get(summaryFile);
      spec = summary.getSpec();
      header = spec.toHeader();
    }
    
    Records records = inputParams.getRecords(p, header);

    Normalizer normalizer = Normalizer.builder()
        .summary(summary)
        .sparse(sparse)
        .defaultTransform(getDefaultTransform())
        .idColumn(Specs.getFieldId(spec, idColumn))
        .build();
    
    PType<Vector> vecPType = outputParams.getVectorPType();
    PCollection<Vector> vecs = normalizer.apply(records.get(), vecPType);
    outputParams.write(vecs, outputFile);
    
    PipelineResult pr = p.done();
    return pr.succeeded() ? 0 : 1;
  }
  
  private Transform getDefaultTransform() {
    String t = transform.toLowerCase(Locale.ENGLISH);
    if ("none".equals(t)) {
      return Transform.NONE;
    } else if ("linear".equals(t)) {
      return Transform.LINEAR;
    } else if ("z".equals(t)) {
      return Transform.Z;
    } else {
      throw new IllegalArgumentException("Unknown default transform type: " + t);
    }
  }
}
