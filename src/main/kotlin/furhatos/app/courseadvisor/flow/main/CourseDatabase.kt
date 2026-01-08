package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.Math.abs

// --- Data Classes (保持不變) ---
data class JsonCourseWrapper(val detailedInformation: DetailedInfo?)
data class DetailedInfo(val course: JsonCourse?, val roundInfos: List<RoundInfo>?)
data class JsonCourse(val courseCode: String?, val title: String?, val credits: Double?, val courseSyllabus: Syllabus?, val gradeScaleCode: String?)
data class Syllabus(val goals: String?, val content: String?, val eligibility: String?, val examComments: String?)
data class RoundInfo(val round: Round?)
data class Round(val courseRoundTerms: List<Term>?)
data class Term(val creditsP1: Double?, val creditsP2: Double?, val creditsP3: Double?, val creditsP4: Double?)

data class CourseInfo(
    val code: String,
    val name: String,
    val credits: Double,
    val availablePeriods: List<String>
)

object CourseDatabase {

    var allCourses: List<CourseInfo> = emptyList()

    // 專門存代碼的 Enum 定義 (給 CourseCode Entity 用)
    var codeEnums: List<String> = emptyList()

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

                        CourseInfo(c.courseCode, c.title, c.credits ?: 0.0, finalPeriods)
                    } else {
                        null
                    }
                }

                // [關鍵] 產生全方位的代碼變體
                // 這能處理: DD2424, LH238V, FAG3109 等各種格式
                codeEnums = allCourses.map { course ->
                    val code = course.code // e.g. "LH238V"

                    // 變體 1: 全字元拆開 (L H 2 3 8 V) - 應對逐字念
                    val allSpaced = code.toCharArray().joinToString(" ")

                    // 變體 2: 英數邊界拆開 (LH 238 V) - 最常見的念法
                    // Regex: 在 "字母接數字" 或 "數字接字母" 的地方切開
                    val splitBoundaries = code.replace(Regex("(?<=[a-zA-Z])(?=[0-9])|(?<=[0-9])(?=[a-zA-Z])"), " ")

                    // 變體 3: 只切開前綴 (LH 238V)
                    val firstDigitIndex = code.indexOfFirst { it.isDigit() }
                    val prefixSplit = if (firstDigitIndex > 0) {
                        code.substring(0, firstDigitIndex) + " " + code.substring(firstDigitIndex)
                    } else code

                    // 組合 Enum 字串: "標準值:標準值,變體1,變體2,變體3"
                    val variants = listOf(code, splitBoundaries, allSpaced, prefixSplit).distinct().joinToString(",")
                    "$code:$variants"
                }

                println("✅ Database loaded: ${allCourses.size} courses.")
                println("✅ Code Enums generated: ${codeEnums.size} (covering all code formats).")
            } else {
                println("❌ Error: /gui/course_all.json not found.")
            }
        } catch (e: Exception) {
            println("❌ Error loading JSON: ${e.message}")
            e.printStackTrace()
        }
    }

    // 給 NLU 的 CourseCode 實體使用
    fun getAllCodeEnums(): List<String> {
        return codeEnums
    }

    // --- 智慧搜尋演算法 (專門給 Wildcard 抓到的名字用) ---
    fun findCourseByName(query: String): CourseInfo? {
        val rawQuery = query.trim()

        // 1. Course Code 精確比對 (移除所有空格雜訊)
        val cleanQueryForCode = rawQuery.filter { it.isLetterOrDigit() }.lowercase()
        val codeMatch = allCourses.find {
            val cleanCode = it.code.filter { c -> c.isLetterOrDigit() }.lowercase()
            cleanQueryForCode.contains(cleanCode) && cleanCode.length >= 3
        }
        if (codeMatch != null) return codeMatch

        // 2. Course Name 模糊搜尋 (計分制)
        val queryTokens = rawQuery.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }

        val bestMatch = allCourses.map { course ->
            val courseNameTokens = course.name.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }
            var matches = 0
            for (qToken in queryTokens) {
                // 字首比對 (解決 Acoustic vs Acoustics)
                if (courseNameTokens.any { cToken -> cToken == qToken || cToken.startsWith(qToken) }) matches++
            }

            var score = 0.0
            if (matches > 0) {
                val precision = matches.toDouble() / queryTokens.size
                val recall = matches.toDouble() / courseNameTokens.size
                // 長度懲罰: 避免短關鍵字 (Sound) 誤判長課名 (Sound in Interaction)
                val lenDiff = abs(courseNameTokens.size - queryTokens.size)
                val lengthPenalty = lenDiff * 0.1
                val fullStringBonus = if (course.name.lowercase().contains(rawQuery.lowercase())) 0.5 else 0.0

                score = (precision + recall + fullStringBonus) - lengthPenalty
            }
            course to score
        }.maxByOrNull { it.second }

        // 門檻設為 0.6，避免太不相關的字也被硬湊
        if (bestMatch != null && bestMatch.second > 0.6) return bestMatch.first

        return null
    }
}