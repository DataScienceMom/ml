In The Attic
============

Hello! `cloudera/ml` is no longer being developed. It will remain available here but will not be updated further.
Please checkout [Oryx](https://github.com/cloudera/oryx) or [Oryx 2](https://github.com/OryxProject/oryx),
which includes much of the ML functionality along with support for random decision forests and ALS-based
recommendation engines.

Now, back to the `README`...

Introduction
------------
Cloudera ML is a collection of Java libraries and commandline tools for performing certain data preparation
and analysis tasks that are often referred to as "advanced analytics" or "machine learning." Our focus is
on simplicity, reliability, easy model interpretation, minimal parameter tuning, and integration with other
tools for data preparation and analysis.

We're kicking things off by introducing a set of tools for performing scalable k-means clustering on
Hadoop. We will expand the set of model fitting algorithms we support over time, but our primary focus will
always be on data preparation and model evaluation. If you'd like to see the currently supported set of commands,
check out the [Cloudera ML Wiki](https://github.com/cloudera/ml/wiki), which has detailed usage information.

Getting Started
---------------
To run this package on your machine, you should first run:

	mvn clean install

There is a script in the `client/bin` directory named "ml" that can be used to run the commands
that this library supports. Run `client/bin/ml help` to see the list of commands and
`client/bin/ml help <cmd>` to get detailed help on the arguments for any individual command.

If you would like to pack everything up and carry it around with you, running

	tar -cvzf ml.tar.gz client/bin/ml client/target/ml-client-0.1.0.jar client/target/lib/

will create a handy little archive with everything you need.

An Example Workflow
-------------------
The [examples/kdd99](https://github.com/cloudera/ml/tree/master/examples/kdd99) directory contains an annotated
workflow that describes the process of finding clusters in some data from
[KDD Cup '99](http://kdd.ics.uci.edu/databases/kddcup99/kddcup99.html), a publicly available
dataset that is widely used as a reference for evaluating clustering algorithms for anomaly detection.
