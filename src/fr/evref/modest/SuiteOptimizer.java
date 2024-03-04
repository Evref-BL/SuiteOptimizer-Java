package fr.evref.modest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Find a combination of tests that maximizes coverage and suite size. This
 * results in minimal coverage per test, for better atomicity.
 * 
 * A list of JaCoCo execution data files (.exec) provides coverage data. Thus,
 * this task depends only on JaCoCo and is compatible with any test framework.
 */
public class SuiteOptimizer {

	/** How many threads to spawn. */
	private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

	/**
	 * Expect at least one path to class files, such as the
	 * <code>jacococli report</code> command, and at least one JaCoCo *.exec file or
	 * directory path, separated by a double dash `--'. Output the chosen
	 * combination on <code>stdout</code>.
	 */
	public static void main(String[] args) {
		// ensure arguments
		if (args.length < 3) {
			error("Expected at least one path to class files, and at least one exec file or directory path, separated by a double dash `--'.");
			return;
		}

		// ensure class directory
		List<File> classesDirectories = new ArrayList<>();
		int argIndex = 0;
		for (; argIndex < args.length; argIndex++) {
			if (args[argIndex].equals("--")) {
				break;
			}
			classesDirectories.add(new File(args[argIndex]));
		}

		// ensure exec files
		List<File> execFiles = new ArrayList<>(args.length - argIndex);
		while (++argIndex < args.length) {
			execFiles.add(new File(args[argIndex]));
		}

		// run suite optimization
		List<Suite> result;
		try {
			result = new SuiteOptimizer().run(classesDirectories, execFiles);
		} catch (Exception e) {
			error(e.getMessage());
			return;
		}

		// output result to stdout
		StringBuilder output = new StringBuilder();
		for (Suite suite : result) {
			BitSet choice = suite.getChoice();
			int i = choice.nextSetBit(0);
			while (true) {
				output.append(i);
				i = choice.nextSetBit(i + 1);
				if (i >= 0) {
					output.append(',');
				} else {
					break;
				}
			}
			output.append('\n');
		}
		System.out.print(output);
	}

	private static void error(String message) {
		System.out.println("ERROR");
		System.err.println(message);
	}

	/**
	 * Find the combinations of execution data files (<code>execFiles</code>) such
	 * that coverage and suite size are maximized. Each <code>execFile</code>
	 * contains coverage data for a single test.
	 * <p>
	 * To find the combinations that maximize the objectives, a power-set lattice of
	 * all possible test suites is lazily built. Each "floor" of the lattice
	 * represents the set of suites with one less test than those on the above
	 * "floor".
	 * <p>
	 * The number of possible tests is <code>n</code> (the size of
	 * <code>execFiles</code>), and the number of tests chosen to be part of a suite
	 * is <code>r</code>. An <code>r-suite</code> is a test suite consisting of
	 * <code>r</code> tests. The root is the suite that contains all tests, it is
	 * the only <code>n-suite</code>.
	 * <p>
	 * There is always at least one solution, and there can be multiple solutions of
	 * the same size.
	 * 
	 * @param classesDirectories class files directory, the same one used with the
	 *                           <code>jacococli report</code> command
	 * @param execFiles          JaCoCo execution data files
	 * @return list of maximally covered and sized suites
	 * @throws IOException
	 */
	public List<Suite> run(final List<File> classesDirectories, final List<File> execFiles) throws Exception {
		final int n = execFiles.size();

		// root is the set of maximum size n and it has maximum coverage
		final Suite root = Suite.root(execFiles.size());
		final int maximumCoverage = root.computeCoverage(classesDirectories, execFiles);

		// begin with singletons (1-sets)
		List<Suite> maximals = new ArrayList<>();
		for (int i = 0; i < execFiles.size(); i++) {
			BitSet choice = new BitSet(n);
			choice.set(i);
			Suite suite = new Suite(choice);

			if (suite.computeCoverage(classesDirectories, execFiles) == maximumCoverage) {
				maximals.add(suite);
			}
		}

		// if some have maximum coverage, any combination will have a redundant test
		if (!maximals.isEmpty()) {
			return maximals;
		}

		// list of (i+1)-sets necessary to eliminate subsets during iteration
		// if a set subsets a nonmaximal set, there is no use computing its coverage
		List<Suite> nonMaximals = new ArrayList<>();

		// (n-1)-sets have a single superset: the root
		for (BitSet choice : new CombinationIterator(n, n - 1)) {
			Suite suite = new Suite(choice);
			if (suite.computeCoverage(classesDirectories, execFiles) == maximumCoverage) {
				maximals.add(suite);
			} else {
				nonMaximals.add(suite);
			}
		}

		// if no (n-1)-set has maximum coverage, only the root does
		if (maximals.isEmpty()) {
			List<Suite> result = new ArrayList<>(1);
			result.add(root);
			return result;
		}

		// iterate over the possible sizes of sets, with r in [2, n-2]
		// from largest, (n-2)-sets, to smallest, 2-sets
		for (int r = n - 2; r > 1; r--) {
			List<Suite> nextMaximals = new ArrayList<>();
			List<Suite> nextNonMaximals = new ArrayList<>();

			// limit the number of concurrent threads to match core count
			ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

			// build all r-sets in parallel
			for (BitSet choice : new CombinationIterator(n, r)) {
				Suite suite = new Suite(choice);

				Runner runner = new Runner(nonMaximals) {
					public void run() {
						// find all supersets of the current set
						for (int i = 0; i < nonMaximals.size(); i++) {
							Suite superset = nonMaximals.get(i);

							// if a superset is not maximal, this set is not maximal
							if (suite.isSubsetOf(superset)) {
								synchronized (nextNonMaximals) {
									nextNonMaximals.add(suite);
								}
								return;
							}
						}

						// analyze suite coverage
						int coverage;
						try {
							coverage = suite.computeCoverage(classesDirectories, execFiles);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}

						if (coverage == maximumCoverage) {
							synchronized (nextMaximals) {
								nextMaximals.add(suite);
							}
						} else {
							synchronized (nextNonMaximals) {
								nextNonMaximals.add(suite);
							}
						}
					}
				};

				threadPool.execute(runner);
			}

			// ensure all r-sets are built
			threadPool.shutdown();
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

			// no r-sets with maximum coverage means (r-1)-sets will not be maximum either
			// this also means that solutions exist in the current maximal sets
			if (nextMaximals.isEmpty()) {
				break;
			}

			// pass the baton to the (r-1)-sets
			maximals = nextMaximals;
			nonMaximals = nextNonMaximals;
		}

		return maximals;
	}
}

/**
 * Hold the current non-maximal supersets explicitly, because the variable
 * defined in an enclosing scope cannot be referenced if it is not final.
 */
abstract class Runner implements Runnable {
	final List<Suite> nonMaximals;

	public Runner(List<Suite> nonMaximals) {
		this.nonMaximals = nonMaximals;
	}
}
