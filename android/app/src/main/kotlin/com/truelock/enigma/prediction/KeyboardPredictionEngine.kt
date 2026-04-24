package com.truelock.enigma.prediction

class KeyboardPredictionEngine private constructor(
    private val terms: List<String>,
    private val termsSet: Set<String>,
    private val priorityWords: Set<String>,
    private val explicitCorrections: Map<String, String>,
    private val deleteIndex: Map<String, Set<String>>,
    private val nextWordMap: Map<String, List<String>>,
) {
    fun suggestions(input: String, maxSuggestions: Int = 3): List<String> {
        val normalized = input.trim().lowercase()
        if (normalized.length < 2) return emptyList()

        val explicit = explicitCorrections[normalized]?.takeIf { it != normalized }
        if (explicit == null && normalized in termsSet) return emptyList()

        val prefixMatches = terms.asSequence()
            .filter { it.startsWith(normalized) && it != normalized }
            .take(12)
            .toList()

        val maxDistance = maxEditDistance(normalized)
        val fuzzyMatches = linkedSetOf<String>()
        collectDeletes(normalized, maxDistance).forEach { delete ->
            deleteIndex[delete]?.forEach { candidate ->
                if (candidate != normalized) {
                    fuzzyMatches += candidate
                }
            }
        }

        val scored = buildSet {
            explicit?.let(::add)
            addAll(prefixMatches)
            addAll(fuzzyMatches)
        }
            .mapNotNull { candidate ->
                val distance = boundedLevenshtein(normalized, candidate, maxDistance)
                val isPrefix = candidate.startsWith(normalized)
                val explicitCandidate = candidate == explicit
                if (!explicitCandidate && !isPrefix && distance !in 1..maxDistance) {
                    null
                } else {
                    Candidate(candidate, distance, scoreCandidate(normalized, candidate, explicit, isPrefix, distance))
                }
            }
            .sortedWith(
                compareByDescending<Candidate> { it.score }
                    .thenBy { termIndex(it.word) }
                    .thenBy { it.word.length }
                    .thenBy { it.word },
            )

        return scored.take(maxSuggestions).map { it.word }
    }

    fun nextSuggestions(previousWord: String, maxSuggestions: Int = 3): List<String> {
        val normalized = previousWord.trim().lowercase()
        if (normalized.length < 2) return emptyList()
        return nextWordMap[normalized].orEmpty().take(maxSuggestions)
    }

    private fun scoreCandidate(
        input: String,
        candidate: String,
        explicit: String?,
        isPrefix: Boolean,
        distance: Int,
    ): Int {
        var score = 0
        if (candidate == explicit) score += 180
        if (candidate in priorityWords) score += 70
        if (isPrefix) {
            score += 120
            score += input.length * 12
            score -= (candidate.length - input.length).coerceAtLeast(0)
        }
        if (distance in 1..2) {
            score += 90 - (distance * 18)
        }
        return score
    }

    private fun termIndex(term: String): Int = terms.indexOf(term).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private data class Candidate(
        val word: String,
        val distance: Int,
        val score: Int,
    )

    companion object {
        fun create(
            terms: List<String>,
            priorityWords: Set<String>,
            explicitCorrections: Map<String, String>,
            nextWordMap: Map<String, List<String>> = emptyMap(),
        ): KeyboardPredictionEngine {
            val normalizedTerms = terms.asSequence()
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 }
                .distinct()
                .toList()
            val deleteIndex = mutableMapOf<String, MutableSet<String>>()
            normalizedTerms.forEach { term ->
                collectDeletes(term, maxEditDistance(term)).forEach { delete ->
                    deleteIndex.getOrPut(delete) { linkedSetOf() }.add(term)
                }
            }
            return KeyboardPredictionEngine(
                terms = normalizedTerms,
                termsSet = normalizedTerms.toSet(),
                priorityWords = priorityWords.map { it.trim().lowercase() }.toSet(),
                explicitCorrections = explicitCorrections.mapKeys { it.key.trim().lowercase() }
                    .mapValues { it.value.trim().lowercase() },
                deleteIndex = deleteIndex,
                nextWordMap = nextWordMap.mapKeys { it.key.trim().lowercase() }
                    .mapValues { entry ->
                        entry.value.asSequence()
                            .map { it.trim().lowercase() }
                            .filter { it.length >= 2 }
                            .distinct()
                            .toList()
                    },
            )
        }

        private fun maxEditDistance(word: String): Int = when {
            word.length >= 5 -> 2
            word.length >= 3 -> 1
            else -> 0
        }

        private fun collectDeletes(word: String, maxDistance: Int): Set<String> {
            val visited = linkedSetOf(word)
            var frontier = linkedSetOf(word)
            repeat(maxDistance) {
                val next = linkedSetOf<String>()
                frontier.forEach { item ->
                    if (item.length <= 1) return@forEach
                    for (index in item.indices) {
                        val deleted = item.removeRange(index, index + 1)
                        if (visited.add(deleted)) {
                            next += deleted
                        }
                    }
                }
                frontier = next
            }
            return visited
        }

        private fun boundedLevenshtein(left: String, right: String, limit: Int): Int {
            if (left == right) return 0
            if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1

            var previous = IntArray(right.length + 1) { it }
            var current = IntArray(right.length + 1)

            for (i in 1..left.length) {
                current[0] = i
                var rowMin = current[0]
                for (j in 1..right.length) {
                    val substitution = if (left[i - 1] == right[j - 1]) 0 else 1
                    current[j] = minOf(
                        previous[j] + 1,
                        current[j - 1] + 1,
                        previous[j - 1] + substitution,
                    )
                    rowMin = minOf(rowMin, current[j])
                }
                if (rowMin > limit) return limit + 1
                val swap = previous
                previous = current
                current = swap
            }

            return previous[right.length]
        }
    }
}
