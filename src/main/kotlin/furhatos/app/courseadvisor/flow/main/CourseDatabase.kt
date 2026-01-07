package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.Math.abs

// --- 1. JSON Parsing Structures ---
data class JsonCourseWrapper(val detailedInformation: DetailedInfo?)
data class DetailedInfo(val course: JsonCourse?, val roundInfos: List<RoundInfo>?)

data class JsonCourse(
    val courseCode: String?,
    val title: String?,
    val credits: Double?,
    val courseSyllabus: Syllabus?,
    val gradeScaleCode: String?
)

data class Syllabus(
    val goals: String?,
    val content: String?,
    val eligibility: String?,
    val examComments: String?
)

data class RoundInfo(val round: Round?)
data class Round(val courseRoundTerms: List<Term>?)
data class Term(
    val creditsP1: Double?,
    val creditsP2: Double?,
    val creditsP3: Double?,
    val creditsP4: Double?
)

// --- 2. Internal Data Structure ---
data class CourseInfo(
    val code: String,
    val name: String,
    val credits: Double,
    val availablePeriods: List<String>
)

object CourseDatabase {

    var allCourses: List<CourseInfo> = emptyList()

    // Strings for NLU EnumEntity definition
    var nluKeywords: List<String> = emptyList()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        try {
            val jsonString = this::class.java.getResource("/gui/course_all.json")?.readText()

            if (jsonString != null) {
                val listType = object : TypeToken<List<JsonCourseWrapper>>() {}.type
                val rawList: List<JsonCourseWrapper> = Gson().fromJson(jsonString, listType)

                allCourses = rawList.mapNotNull { wrapper ->
                    val c = wrapper.detailedInformation?.course
                    val rounds = wrapper.detailedInformation?.roundInfos

                    if (c != null && c.courseCode != null && c.title != null) {

                        val periodsSet = mutableSetOf<String>()
                        rounds?.forEach { r ->
                            r.round?.courseRoundTerms?.forEach { t ->
                                if ((t.creditsP1 ?: 0.0) > 0) periodsSet.add("P1")
                                if ((t.creditsP2 ?: 0.0) > 0) periodsSet.add("P2")
                                if ((t.creditsP3 ?: 0.0) > 0) periodsSet.add("P3")
                                if ((t.creditsP4 ?: 0.0) > 0) periodsSet.add("P4")
                            }
                        }
                        val finalPeriods = if (periodsSet.isEmpty()) listOf("P1") else periodsSet.toList().sorted()

                        CourseInfo(
                            code = c.courseCode,
                            name = c.title,
                            credits = c.credits ?: 0.0,
                            availablePeriods = finalPeriods
                        )
                    } else {
                        null
                    }
                }

                // [CRITICAL UPDATE] Robust Course Code Variant Generation
                // This handles DD2424, LH238V, FAG3109, etc.
                val codeEnums = allCourses.map { course ->
                    val code = course.code // e.g., "LH238V"

                    // Variant 1: Spaced characters (ASR often spells it out)
                    // "L H 2 3 8 V"
                    val allSpaced = code.map { "$it" }.joinToString(" ")

                    // Variant 2: Split distinct groups of Letters vs Numbers
                    // This regex puts a space whenever a Letter touches a Number or vice-versa
                    // "LH238V" -> "LH 238 V"
                    // "FAG3109" -> "FAG 3109"
                    // "DD2424" -> "DD 2424"
                    val splitBoundaries = code.replace(Regex("(?<=[a-zA-Z])(?=[0-9])|(?<=[0-9])(?=[a-zA-Z])"), " ")

                    // Variant 3: Prefix Split (Split only after the first letter group)
                    // Most common way to say it: "LH 238V"
                    // We find the first digit and split there.
                    val firstDigitIndex = code.indexOfFirst { it.isDigit() }
                    val prefixSplit = if (firstDigitIndex > 0) {
                        code.substring(0, firstDigitIndex) + " " + code.substring(firstDigitIndex)
                    } else {
                        code
                    }

                    // Combine all unique variants into the NLU format: "StandardValue:Synonym1,Synonym2..."
                    val variants = listOf(code, allSpaced, splitBoundaries, prefixSplit)
                        .distinct() // Remove duplicates
                        .joinToString(",")

                    "$code:$variants"
                }

                val names = allCourses.map { it.name }

                // Combine names and code variants for the NLU
                nluKeywords = names + codeEnums

                println("✅ Database loaded: ${allCourses.size} courses.")
                println("✅ NLU Keywords generated with robust code synonyms.")
            } else {
                println("❌ Error: /gui/course_all.json not found.")
            }
        } catch (e: Exception) {
            println("❌ Error loading JSON: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getNluList(): List<String> {
        return nluKeywords
    }

    // --- Smart Search Algorithm ---
    fun findCourseByName(query: String): CourseInfo? {
        val rawQuery = query.trim()

        // 1. [Course Code Fix]
        // Remove ALL non-alphanumeric chars.
        // Input "L H 2 3 8 V" becomes "lh238v", matches DB "LH238V"
        val cleanQueryForCode = rawQuery.filter { it.isLetterOrDigit() }.lowercase()

        // Use 'contains' to allow flexibility (e.g. "add LH238V")
        val codeMatch = allCourses.find {
            val cleanCode = it.code.filter { c -> c.isLetterOrDigit() }.lowercase()
            // Ensure length >= 3 to avoid matching "I am 20" to a course named "20"
            cleanQueryForCode.contains(cleanCode) && cleanCode.length >= 3
        }

        if (codeMatch != null) {
            println("🔎 Code Match: '$query' -> ${codeMatch.code}")
            return codeMatch
        }

        // 2. [Course Name Scoring]
        val queryTokens = rawQuery.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() }

        val bestMatch = allCourses.map { course ->
            val courseNameTokens = course.name.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() }

            var matches = 0
            for (qToken in queryTokens) {
                // StartsWith logic handles plurals (Acoustic vs Acoustics)
                if (courseNameTokens.any { cToken -> cToken == qToken || cToken.startsWith(qToken) }) {
                    matches++
                }
            }

            var score = 0.0
            if (matches > 0) {
                val precision = matches.toDouble() / queryTokens.size
                val recall = matches.toDouble() / courseNameTokens.size
                val lenDiff = abs(courseNameTokens.size - queryTokens.size)
                val lengthPenalty = lenDiff * 0.1
                val fullStringBonus = if (course.name.lowercase().contains(rawQuery.lowercase())) 0.5 else 0.0

                score = (precision + recall + fullStringBonus) - lengthPenalty
            }

            course to score
        }.maxByOrNull { it.second }

        if (bestMatch != null && bestMatch.second > 0.6) {
            println("🔎 Smart Name Match: '$query' -> '${bestMatch.first.name}' (Score: ${String.format("%.2f", bestMatch.second)})")
            return bestMatch.first
        }

        println("❌ No good match found for '$query'")
        return null
    }
}