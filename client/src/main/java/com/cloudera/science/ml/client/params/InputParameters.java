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
package com.cloudera.science.ml.client.params;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.crunch.PCollection;
import org.apache.crunch.Pipeline;
import org.apache.crunch.io.From;
import org.apache.crunch.types.PType;
import org.apache.crunch.types.avro.AvroType;
import org.apache.mahout.math.Vector;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.CommaParameterSplitter;
import com.cloudera.science.ml.client.cmd.CommandException;
import com.cloudera.science.ml.core.records.Record;
import com.cloudera.science.ml.mahout.types.MLWritables;
import com.cloudera.science.ml.parallel.normalize.StringSplitFn;
import com.cloudera.science.ml.parallel.types.MLAvros;
import com.cloudera.science.ml.parallel.types.MLRecords;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Class specifies the common input parameters that may be used across ML commands
 *
 * Following are the list of commands that are supported
 *
 * <PRE>
 * <b>--input-paths</b>
 *     Comma separated paths to be used as input
 *
 * <b>--format</b>
 *     format of the Input. Possible values are seq, text and avro
 *
 * <b>--delim</b>
 *     Delimited to be used for text input files. Default is ','
 *
 * <b>--ignore-lines</b>
 *     Regular expression based on which lines in text input file shall be ignored
 * </PRE>
 */
public class InputParameters {

  public static final String TEXT = "text";
  public static final String FORMAT_SEQ = "seq";
  public static final String FORMAT_AVRO = "avro";

    @Parameter(names = "--input-paths",
      description = "CSV of the input paths to consider",
      splitter = CommaParameterSplitter.class,
      required = true)
  private List<String> inputPaths;

  @Parameter(names = "--format",
      description = "One of 'seq', 'text', or 'avro' to describe the format of the input files",
      required = true)
  private String format;
  
  @Parameter(names = "--delim",
      description = "For text files, the delimiter to use for separate fields")
  private String delim = ",";
  
  @Parameter(names = "--ignore-lines",
      description = "Any lines that match this regular expression in a text file will be ignored by the parser")
  private String ignoreLines;
  
  public String getDelimiter() {
    return delim;
  }
  
  public PCollection<Vector> getVectorsFromPath(Pipeline pipeline, String path) {
    return getVectors(pipeline, Collections.singletonList(path));
  }
  
  public PCollection<Vector> getVectors(Pipeline pipeline) {
    return getVectors(pipeline, inputPaths);
  }
  
  private PCollection<Vector> getVectors(final Pipeline pipeline, List<String> paths) {
    format = format.toLowerCase(Locale.ENGLISH);
    if (TEXT.equals(format)) {
      throw new IllegalArgumentException("Vectors must be in 'seq' or 'avro' format");
    }
    PCollection<Vector> ret;
    if (FORMAT_SEQ.equals(format)) {
      ret = from(paths, new Function<String, PCollection<Vector>>() {
        @Override
        public PCollection<Vector> apply(String input) {
          return pipeline.read(From.sequenceFile(input, MLWritables.vector()));
        }
      });
    } else if (FORMAT_AVRO.equals(format)) {
      ret = from(paths, new Function<String, PCollection<Vector>>() {
        @Override
        public PCollection<Vector> apply(String input) {
          return pipeline.read(From.avroFile(input, MLAvros.vector()));
        }
      });
    } else {
      throw new CommandException("Unknown format: " + format);
    }
    return ret;
  }

  public PCollection<Record> getRecords(final Pipeline pipeline) {
    format = format.toLowerCase(Locale.ENGLISH);
    PCollection<Record> ret;
    if (TEXT.equals(format)) {
      PCollection<String> text = fromInputs(new Function<String, PCollection<String>>() {
        @Override
        public PCollection<String> apply(String input) {
          return pipeline.readTextFile(input);
        }
      });
      Pattern pattern = ignoreLines == null ? null : Pattern.compile(ignoreLines);
      ret = StringSplitFn.apply(text, delim, pattern);
    } else if (FORMAT_SEQ.equals(format)) {
      final PType<Record> ptype = MLRecords.vectorRecord(MLWritables.vector());
      ret = fromInputs(new Function<String, PCollection<Record>>() {
        @Override
        public PCollection<Record> apply(String input) {
          return pipeline.read(From.sequenceFile(input, ptype));
        }
      });
    } else if (FORMAT_AVRO.equals(format)) {
      final AvroType<Record> ptype = (AvroType<Record>) MLRecords.vectorRecord(MLAvros.vector());
      ret = fromInputs(new Function<String, PCollection<Record>>() {
        @Override
        public PCollection<Record> apply(String input) {
          return pipeline.read(From.avroFile(input, ptype));
        }
      });
    } else {
      throw new CommandException("Unknown format: " + format);
    }
    return ret;
  }

  private <T> PCollection<T> fromInputs(Function<String, PCollection<T>> f) {
    return from(inputPaths, f);
  }
  
  private static <T> PCollection<T> from(List<String> paths, Function<String, PCollection<T>> f) {
    PCollection<T> ret = null;
    for (PCollection<T> p : Lists.transform(paths, f)) {
      if (ret == null) {
        ret = p;
      } else {
        ret = ret.union(p);
      }
    }
    return ret;
  }
}
