package com.varav.textbrush

import android.graphics.PointF

object BezierSplineUtil {

    fun computeControlPoints(knots: Array<PointF>): Array<Pair<PointF, PointF>> {
        val n = knots.size - 1
        val cp = Array(n) { Pair(PointF(), PointF())}

        // Special case: bezier curve should be a straight line
        if (n == 1) {
            // 3P1 = 2P0 + P3
            var x = (2 * knots[0].x + knots[1].x) / 3f
            var y = (2 * knots[0].y + knots[1].y) / 3f
            cp[0].first.set(x, y)

            // P2 = 2P1 - P0
            x = 2 * cp[0].first.x - knots[0].x
            y = 2 * cp[0].first.y - knots[0].y
            cp[0].second.set(x, y)
            return cp
        }

        // Calculate first bezier control points
        // Right hand side vector
        val rhs = FloatArray(n)

        // Set right hand side X values
        for (i in 1 until n - 1) {
            rhs[i] = 4 * knots[i].x + 2 * knots[i + 1].x
        }
        rhs[0] = knots[0].x + 2 * knots[1].x
        rhs[n - 1] = (8 * knots[n - 1].x + knots[n].x) / 2f

        // Get first control points X-values
        val x = getFirstControlPoints(rhs)

        // Set right hand side Y values
        for (i in 1 until n - 1) {
            rhs[i] = 4 * knots[i].y + 2 * knots[i + 1].y
        }
        rhs[0] = knots[0].y + 2 * knots[1].y
        rhs[n - 1] = (8 * knots[n - 1].y + knots[n].y) / 2f

        // Get first control points Y-values
        val y = getFirstControlPoints(rhs)
        for (i in 0 until n) {
            // First control point
            cp[i].first.set(x[i], y[i])

            // Second control point
            if (i < n - 1) {
                val xx = 2 * knots[i + 1].x - x[i + 1]
                val yy = 2 * knots[i + 1].y - y[i + 1]
                cp[i].second.set(xx, yy)
            } else {
                val xx = (knots[n].x + x[n - 1]) / 2
                val yy = (knots[n].y + y[n - 1]) / 2
                cp[i].second.set(xx, yy)
            }
        }
        return cp
    }

    private fun getFirstControlPoints(rhs: FloatArray): FloatArray {
        val n = rhs.size
        val x = FloatArray(n) // Solution vector
        val tmp = FloatArray(n) // Temp workspace
        var b = 2.0f
        x[0] = rhs[0] / b

        // Decomposition and forward substitution
        for (i in 1 until n) {
            tmp[i] = 1 / b
            b = (if (i < n - 1) 4.0f else 3.5f) - tmp[i]
            x[i] = (rhs[i] - x[i - 1]) / b
        }

        // Backsubstitution
        for (i in 1 until n) {
            x[n - i - 1] -= tmp[n - i] * x[n - i]
        }
        return x
    }
}