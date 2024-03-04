package fr.evref.modest;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CombinationIterator implements Iterable<BitSet>, Iterator<BitSet> {

	private final int n;
	private final int r;
	private BitSet current;

	public CombinationIterator(int n, int r) {
		this.n = n;
		this.r = r;
		current = new BitSet(n);
		current.set(0, r);
	}

	@Override
	public boolean hasNext() {
		return current != null;
	}

	@Override
	public BitSet next() {
		if (current == null) {
			throw new NoSuchElementException();
		}

		// only one result: the current set
		if (n == 0 || n == r) {
			BitSet result = current;
			current = null;
			return result;
		}

		// result is the current set, prepare current for next iteration
		BitSet result = (BitSet) current.clone();

		// find the first 1 from the end
		int pivot = current.previousSetBit(n - 1);

		// if it has a 0 to its right, flip both
		if (pivot != n - 1 && !current.get(pivot + 1)) {
			current.clear(pivot);
			current.set(pivot + 1);
			return result;
		}

		// count the number of 1s until the next 0
		int nextZero = current.previousClearBit(pivot - 1);
		int ones = pivot - nextZero;

		// find the next 1, we know there is a 0 to its right
		pivot = current.previousSetBit(nextZero - 1);

		// if no more 1, no next combination is possible
		if (pivot == -1) {
			current = null;
			return result;
		}

		// swap pivot with 0 to its right, and bring all 1s immediately after
		current.clear(pivot);
		current.set(pivot + 1, pivot + ones + 2);
		current.clear(pivot + ones + 2, n);

		return result;
	}

	@Override
	public Iterator<BitSet> iterator() {
		return this;
	}

	/**
	 * Calculate the number of combinations, known as the binomial coefficient.
	 * 
	 * @return number of combinations
	 */
	public long size() {
		return size(n, r);
	}

	/**
	 * Calculate the binomial coefficient: the number of combinations of size
	 * <code>r</code> in a set of <code>n</code> elements.
	 * 
	 * @param n number of elements
	 * @param r size of combinations
	 * @return number of combinations
	 */
	public static long size(int n, int r) {
		long result = 1;
		if (r > n - r)
			r = n - r;
		for (int i = 1; i <= r; i++)
			result = result * (n - i + 1) / i;
		return result;
	}
}