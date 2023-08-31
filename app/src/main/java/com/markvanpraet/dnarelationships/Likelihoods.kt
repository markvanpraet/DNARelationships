package com.markvanpraet.dnarelationships

data class Likelihoods(val cmBasis: Double, val likelihood: DoubleArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Likelihoods

        if (!likelihood.contentEquals(other.likelihood)) return false

        return true
    }

    override fun hashCode(): Int {
        return likelihood.contentHashCode()
    }
}