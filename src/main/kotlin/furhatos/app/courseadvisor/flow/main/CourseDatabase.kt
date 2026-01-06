package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

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

                        // 解析 Period
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

                // 產生 NLU 關鍵字清單
                val names = allCourses.map { it.name }
                val codes = allCourses.map { it.code }
                nluKeywords = names + codes

                println("✅ Database loaded: ${allCourses.size} courses.")
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

    // --- [核心修改] 智慧計分搜尋演算法 ---
    fun findCourseByName(query: String): CourseInfo? {
        // 正規化使用者輸入
        val rawQuery = query.trim()

        // 1. [解決 Course Code 問題] 強力正規化
        // 把 "D D 2 4 2 4" 或 "DD 2424" 變成 "dd2424"
        val cleanQueryForCode = rawQuery.filter { it.isLetterOrDigit() }.lowercase()

        val codeMatch = allCourses.find {
            it.code.filter { c -> c.isLetterOrDigit() }.lowercase() == cleanQueryForCode
        }
        if (codeMatch != null) {
            println("🔎 Code Match: '$query' -> ${codeMatch.code}")
            return codeMatch
        }

        // 2. [解決 Sound / Music Acoustic 問題] 計分搜尋
        // 將查詢語句拆成單字 (Tokens)
        val queryTokens = rawQuery.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "") // 移除標點
            .split(" ")
            .filter { it.isNotBlank() }

        // 尋找最佳匹配
        val bestMatch = allCourses.map { course ->
            val courseNameTokens = course.name.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() }

            var matches = 0
            for (qToken in queryTokens) {
                // [關鍵] 只要課程名稱裡的字 "開頭符合" 查詢字，就算分
                // 這樣 "acoustic" 可以匹配 "acoustics"
                if (courseNameTokens.any { cToken -> cToken == qToken || cToken.startsWith(qToken) }) {
                    matches++
                }
            }

            // 計算分數 (Jaccard 相似度概念)
            // 分數 = 匹配單字數 / 查詢與課名的總單字數 (避免短關鍵字誤判長課名)
            var score = 0.0
            if (matches > 0) {
                // 加權：如果完全包含使用者輸入的字串，加分
                val fullStringBonus = if (course.name.lowercase().contains(rawQuery.lowercase())) 1.0 else 0.0

                // 核心分數：匹配數量越高越好，但若課程名稱很長而只匹配到一個字，分數會被拉低
                // 例如 Query: "Sound" (1 token)
                // - Course "Sound": matches=1, len=1. Score = high
                // - Course "Sound in Interaction": matches=1, len=3. Score = low
                val precision = matches.toDouble() / queryTokens.size
                val recall = matches.toDouble() / courseNameTokens.size

                score = (precision + recall + fullStringBonus)
            }

            course to score
        }.maxByOrNull { it.second } // 取出分數最高的

        // 設定一個最低門檻，避免亂抓
        if (bestMatch != null && bestMatch.second > 0.8) {
            println("🔎 Smart Name Match: '$query' -> '${bestMatch.first.name}' (Score: ${String.format("%.2f", bestMatch.second)})")
            return bestMatch.first
        }

        // 如果分數都很低，回傳 null
        println("❌ No good match found for '$query'")
        return null
    }
}