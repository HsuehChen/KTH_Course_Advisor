package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- 1. JSON 解析結構 ---
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

// --- 2. 內部使用的資料結構 ---
data class CourseInfo(
    val code: String,
    val name: String,
    val credits: Double,
    val availablePeriods: List<String>
)

object CourseDatabase {

    var allCourses: List<CourseInfo> = emptyList()
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

                val names = allCourses.map { it.name }
                val codes = allCourses.map { it.code }
                nluKeywords = names + codes

                println("✅ Database loaded: ${allCourses.size} courses.")
            } else {
                println("❌ Error: /gui/course_all.json not found in resources.")
            }
        } catch (e: Exception) {
            println("❌ Error loading JSON: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getNluList(): List<String> {
        return nluKeywords
    }

    // --- [核心修改] 智慧搜尋演算法 ---
    fun findCourseByName(query: String): CourseInfo? {
        val rawQuery = query.trim()

        // 1. 正規化：移除所有非英數字元 (處理 "D D 2 4 2 4" -> "DD2424")
        val cleanQueryForCode = rawQuery.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

        // 2. 搜尋 Course Code (最優先)
        val codeMatch = allCourses.find {
            it.code.replace(Regex("[^a-zA-Z0-9]"), "").lowercase() == cleanQueryForCode
        }
        if (codeMatch != null) {
            println("🔎 Exact Code Match: ${codeMatch.code}")
            return codeMatch
        }

        // 3. 搜尋 Course Name (計分制)
        // 我們會給每個候選人打分數，最後選分數最高的

        // 將查詢語句拆成單字 (Tokens)，例如 "music acoustic" -> ["music", "acoustic"]
        val queryTokens = rawQuery.lowercase().split(" ").filter { it.isNotEmpty() }

        val bestMatch = allCourses.map { course ->
            val courseNameLower = course.name.lowercase()
            var score = 0

            // A. 完全包含 (最重要)
            if (courseNameLower == rawQuery.lowercase()) {
                score += 1000
            }
            // B. 包含字串 (次重要)
            else if (courseNameLower.contains(rawQuery.lowercase())) {
                score += 500
                // [關鍵] 懲罰長度差異：如果使用者說 "Sound"，"Sound" (5字) 分數會比 "Sound and Vibration" (19字) 高
                // 差異越小扣分越少
                val lengthDiff = courseNameLower.length - rawQuery.length
                score -= lengthDiff // 越接近原始長度分數越高
            }

            // C. 單字比對 (解決 "Music Acoustic" vs "Music Acoustics")
            var tokenMatches = 0
            for (token in queryTokens) {
                if (courseNameLower.contains(token)) {
                    tokenMatches++
                }
            }
            // 如果所有單字都出現了，加分
            if (tokenMatches > 0) {
                score += tokenMatches * 100
            }

            // 回傳 Pair(課程, 分數)
            course to score
        }.filter {
            it.second > 0 // 只保留有相關的
        }.maxByOrNull {
            it.second // 取出分數最高的
        }

        if (bestMatch != null) {
            println("🔎 Smart Match: '${rawQuery}' -> '${bestMatch.first.name}' (Score: ${bestMatch.second})")
            return bestMatch.first
        }

        return null
    }
}