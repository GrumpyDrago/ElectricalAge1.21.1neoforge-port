package mods.eln.sim.mna

import kotlin.math.abs

/**
 * The inverse of a matrix in double-double precision.
 *
 * Does what `org.apache.commons.numbers.core.DD` would, without an object for each number: every element is the unevaluated
 * sum of [hi] and [lo], row after row. The error-free transformations are those of Dekker and Knuth; the sums, products
 * and quotients are the accurate algorithms of Joldes, Muller and Popescu. `Math.fma` is avoided, since it is
 * extremely slow where the processor does not have it.
 */
internal class DdInverse private constructor(private val size: Int, private val hi: DoubleArray, private val lo: DoubleArray) {

    private val nonZero = IntArray(size)

    /** [result] = inverse * [vector] */
    fun multiply(vector: DoubleArray, result: DoubleArray) {
        val count = collectNonZero(vector)
        for (row in 0 until size) result[row] = dot(row, vector, count)
    }

    /** One element of inverse * [vector]. */
    fun multiplyRow(row: Int, vector: DoubleArray): Double = dot(row, vector, collectNonZero(vector))

    fun get(row: Int, column: Int): Double = hi[row * size + column]

    /** Only sources put something into the vector, so most of it is zero. */
    private fun collectNonZero(vector: DoubleArray): Int {
        var count = 0
        for (i in 0 until size) if (vector[i] != 0.0) nonZero[count++] = i
        return count
    }

    private fun dot(row: Int, vector: DoubleArray, count: Int): Double {
        val base = row * size
        var sh = 0.0
        var sl = 0.0
        for (k in 0 until count) {
            val column = nonZero[k]
            val xh = hi[base + column]
            val y = vector[column]

            // product, double-double times double
            val ph0 = xh * y
            val pl0 = productError(xh, y, ph0) + lo[base + column] * y
            val ph = ph0 + pl0
            val pl = pl0 - (ph - ph0)

            // sum
            val a = sh + ph
            val ab = a - sh
            val aErr = (sh - (a - ab)) + (ph - ab)
            val b = sl + pl
            val bb = b - sl
            val bErr = (sl - (b - bb)) + (pl - bb)
            val c = aErr + b
            val vh = a + c
            val vl = c - (vh - a)
            val w = bErr + vl
            sh = vh + w
            sl = w - (sh - vh)
        }
        return sh
    }

    companion object {
        private const val SPLIT = 134217729.0 // 2^27 + 1

        private fun productError(x: Double, y: Double, product: Double): Double {
            val xs = x * SPLIT
            val x1 = xs - (xs - x)
            val x2 = x - x1
            val ys = y * SPLIT
            val y1 = ys - (ys - y)
            val y2 = y - y1
            return x2 * y2 - (product - x1 * y1 - x2 * y1 - x1 * y2)
        }

        /** Throws [IllegalStateException] if [matrix] is singular. */
        @JvmStatic
        fun of(matrix: Array<DoubleArray>): DdInverse {
            val n = matrix.size
            val h = Array(n) { matrix[it].clone() }
            val l = Array(n) { DoubleArray(n) }
            val pivots = IntArray(n)

            // Where the pivot row is not zero, and its high parts split in two.
            val columns = IntArray(n)
            val high1 = DoubleArray(n)
            val high2 = DoubleArray(n)

            // Gauss-Jordan in place, with partial pivoting.
            for (k in 0 until n) {
                var pivotRow = k
                var pivotMagnitude = 0.0
                for (row in k until n) {
                    val magnitude = abs(h[row][k])
                    if (magnitude > pivotMagnitude) {
                        pivotMagnitude = magnitude
                        pivotRow = row
                    }
                }
                check(pivotMagnitude != 0.0) { "Matrix is singular" }
                pivots[k] = pivotRow
                if (pivotRow != k) {
                    val th = h[k]; h[k] = h[pivotRow]; h[pivotRow] = th
                    val tl = l[k]; l[k] = l[pivotRow]; l[pivotRow] = tl
                }

                val kh = h[k]
                val kl = l[k]
                val dh = kh[k]
                val dl = kl[k]
                kh[k] = 1.0
                kl[k] = 0.0
                var count = 0
                for (c in 0 until n) {
                    val xh = kh[c]
                    if (xh == 0.0) continue
                    // quotient
                    val th = xh / dh
                    val ph0 = dh * th
                    val pl0 = productError(dh, th, ph0) + dl * th
                    val ph = ph0 + pl0
                    val pl = pl0 - (ph - ph0)
                    val tl = ((xh - ph) + (kl[c] - pl)) / dh
                    val qh = th + tl
                    kh[c] = qh
                    kl[c] = tl - (qh - th)

                    val s = qh * SPLIT
                    val s1 = s - (s - qh)
                    high1[count] = s1
                    high2[count] = qh - s1
                    columns[count++] = c
                }

                for (row in 0 until n) {
                    if (row == k) continue
                    val rh = h[row]
                    val fh = rh[k]
                    if (fh == 0.0) continue
                    val rl = l[row]
                    eliminate(rh, rl, fh, rl[k], k, kh, kl, columns, count, high1, high2)
                }
            }

            // Undo the row exchanges, as column exchanges.
            for (k in n - 1 downTo 0) {
                val p = pivots[k]
                if (p == k) continue
                for (row in 0 until n) {
                    val rh = h[row]
                    val th = rh[k]; rh[k] = rh[p]; rh[p] = th
                    val rl = l[row]
                    val tl = rl[k]; rl[k] = rl[p]; rl[p] = tl
                }
            }

            val hi = DoubleArray(n * n)
            val lo = DoubleArray(n * n)
            for (row in 0 until n) {
                System.arraycopy(h[row], 0, hi, row * n, n)
                System.arraycopy(l[row], 0, lo, row * n, n)
            }
            return DdInverse(n, hi, lo)
        }

        /** row -= factor * pivot row */
        private fun eliminate(
            rh: DoubleArray, rl: DoubleArray, fh: Double, fl: Double, k: Int, kh: DoubleArray, kl: DoubleArray,
            columns: IntArray, count: Int, high1: DoubleArray, high2: DoubleArray
        ) {
            rh[k] = 0.0
            rl[k] = 0.0
            val fs = fh * SPLIT
            val f1 = fs - (fs - fh)
            val f2 = fh - f1
            for (i in 0 until count) {
                val c = columns[i]
                val yh = kh[c]

                // product
                val ph0 = fh * yh
                val y1 = high1[i]
                val y2 = high2[i]
                val pl0 = (f2 * y2 - (ph0 - f1 * y1 - f2 * y1 - f1 * y2)) + (fh * kl[c] + fl * yh)
                val ph = ph0 + pl0
                val pl = pl0 - (ph - ph0)

                // difference
                val xh = rh[c]
                val xl = rl[c]
                val a = xh - ph
                val ab = a - xh
                val aErr = (xh - (a - ab)) - (ph + ab)
                val b = xl - pl
                val bb = b - xl
                val bErr = (xl - (b - bb)) - (pl + bb)
                val d = aErr + b
                val vh = a + d
                val vl = d - (vh - a)
                val w = bErr + vl
                val sum = vh + w
                rh[c] = sum
                rl[c] = w - (sum - vh)
            }
        }
    }
}
