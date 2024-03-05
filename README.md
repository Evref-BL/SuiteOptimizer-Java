# Suite Optimizer for Java

Optimize a set of tests by identifying combinations that simultaneously:
- Maximize total coverage.
- Maximize suite size.
- Eliminate redundant tests, i.e., tests whose removal does not reduce coverage.

This optimization strategy results in minimal coverage per test, which contributes to improved test atomicity.

Coverage data is obtained using [JaCoCo](https://www.jacoco.org/).

> [!NOTE]
> This is developed as part of the [Modest](https://github.com/Evref-BL/Modest) project in order to optimize generated test suites.  
> However, it is applicable to any Java project.

## Usage

The optimizer takes as arguments:
- `classpaths`, paths to class files or directories of the project under test.
- `--`, to separate with the following.
- `execpaths`, set of paths to JaCoCo execution data files (.exec), each one corresponding to the coverage of an atomic test.

```sh
java -jar suite-optimizer.jar <classpaths...> -- <execpaths...>
```

The output shows the optimized combinations of equal size, with each combination listed on a separate line in the standard output.
Each test is identified by its index in execpaths, and the tests within a combination are separated by commas.

> [!NOTE]
> The [Atomic Coverage Analyzer](https://github.com/Evref-BL/AtomicCoverageAnalyzer-Java) can be used to generate an exec file per test method.

## Build

Using Maven:
```sh
mvn clean install
```
The generated jar can be found in the `target` folder.
