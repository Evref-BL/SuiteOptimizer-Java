package fr.evref.modest;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.tools.ExecFileLoader;

/**
 * Represents a test suite combination that is part of the powerset of a base
 * suite. Acts as a node in the corresponding lattice.
 */
public class Suite {

	// ======================
	// ===== Attributes =====
	// ======================

	/**
	 * JaCoCo execution data files constituting a suite.
	 */
	private BitSet choice;

	// ==================================
	// ===== Constructors & Getters =====
	// ==================================

	public Suite(BitSet choice) {
		this.choice = choice;
	}

	public BitSet getChoice() {
		return choice;
	}

	// ===================
	// ===== Lattice =====
	// ===================

	/**
	 * Make the root suite containing all the <code>n</code> possible tests.
	 * 
	 * @param n total number of tests
	 * @return the root suite
	 */
	public static Suite root(int n) {
		BitSet choice = new BitSet(n);
		choice.set(0, n);
		return new Suite(choice);
	}

	/**
	 * True if this suite is a subset of the given suite.
	 * 
	 * @param suite a possible superset
	 */
	public boolean isSubsetOf(Suite suite) {
		BitSet subset = (BitSet) choice.clone();
		subset.and(suite.choice);
		return subset.equals(choice);
	}

	// ================================
	// ===== Optimization Problem =====
	// ================================

	/**
	 * Calculate the total instruction coverage of the suite over a project.
	 * 
	 * @param classesDirectories locations of Java class files
	 * @return total instruction coverage
	 * @throws IOException
	 */
	public int computeCoverage(List<File> classesDirectories, List<File> execFiles) throws IOException {
		// load all exec files to merge data into a single store
		ExecFileLoader execFileLoader = new ExecFileLoader();
		for (int i = choice.nextSetBit(0); i >= 0; i = choice.nextSetBit(i + 1)) {
			execFileLoader.load(execFiles.get(i));
		}

		// run coverage analysis of the given classes directories using the exec data
		CoverageBuilder coverageBuilder = new CoverageBuilder();
		Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
		for (File classesDirectory : classesDirectories) {
			analyzer.analyzeAll(classesDirectory);
		}

		// obtain total instruction coverage
		return coverageBuilder.getBundle("").getInstructionCounter().getCoveredCount();
	}
}
