package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// --- 1. JSON 解析結構 (擴充以支援 Period 解析) ---
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

// 用來解析學期 (Period) 的深層結構
data class RoundInfo(val round: Round?)
data class Round(val courseRoundTerms: List<Term>?)
data class Term(
    val creditsP1: Double?,
    val creditsP2: Double?,
    val creditsP3: Double?,
    val creditsP4: Double?
)

// --- 2. 程式內部使用的乾淨格式 ---
data class CourseInfo(
    val code: String,
    val name: String,
    val credits: Double,
    // 解析出來的真實可用時段，例如 ["P1", "P2"]
    val availablePeriods: List<String>
)

object CourseDatabase {

    // 儲存所有解析後的課程物件
    var allCourses: List<CourseInfo> = emptyList()

    // 儲存給 NLU 使用的關鍵字清單 (這裡暫時保留，若使用 WildcardEntity 則主要依賴 findCourseByName)
    var nluKeywords: List<String> = emptyList()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        try {
            // 讀取 JSON 檔案
            // 您原本的路徑是 "/gui/course_all.json"，這裡沿用您的設定
            // 若讀不到，請確認檔案是否真的在 src/main/resources/gui/ 底下
            val jsonString = this::class.java.getResource("/gui/course_all.json")?.readText()

            if (jsonString != null) {
                val listType = object : TypeToken<List<JsonCourseWrapper>>() {}.type
                val rawList: List<JsonCourseWrapper> = Gson().fromJson(jsonString, listType)

                // 轉換並過濾無效資料
                allCourses = rawList.mapNotNull { wrapper ->
                    val c = wrapper.detailedInformation?.course
                    val rounds = wrapper.detailedInformation?.roundInfos

                    if (c != null && c.courseCode != null && c.title != null) {

                        // --- [關鍵改進] 解析真實的 Period ---
                        val periodsSet = mutableSetOf<String>()

                        // 遍歷所有的開課回合，檢查哪個時段有學分
                        rounds?.forEach { r ->
                            r.round?.courseRoundTerms?.forEach { t ->
                                if ((t.creditsP1 ?: 0.0) > 0) periodsSet.add("P1")
                                if ((t.creditsP2 ?: 0.0) > 0) periodsSet.add("P2")
                                if ((t.creditsP3 ?: 0.0) > 0) periodsSet.add("P3")
                                if ((t.creditsP4 ?: 0.0) > 0) periodsSet.add("P4")
                            }
                        }

                        // 如果完全沒抓到 Period (防呆)，預設給 P1，並排序
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

                // 產生關鍵字清單 (給 NLU 的 EnumEntity 用，若有需要)
                val names = allCourses.map { it.name }
                val codes = allCourses.map { it.code }
                nluKeywords = names + codes

                println("✅ Database loaded: ${allCourses.size} courses.")
                if (allCourses.isNotEmpty()) {
                    println("ℹ️ Example: ${allCourses.first().name} runs in ${allCourses.first().availablePeriods}")
                }
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

    // --- [關鍵改進] 模糊搜尋方法 ---
    fun findCourseByName(query: String): CourseInfo? {
        val q = query.trim().lowercase()

        // 1. 先嘗試代碼完全比對 (例如 "DD2424")
        val exactCode = allCourses.find { it.code.equals(q, ignoreCase = true) }
        if (exactCode != null) return exactCode

        // 2. 再嘗試名稱「包含」比對 (例如 "Energy Business" 能找到 "Energy Business Models")
        // 我們優先找字串長度最短的匹配項 (通常代表最精準的匹配)，或者直接回傳第一個
        val matches = allCourses.filter {
            it.name.lowercase().contains(q)
        }

        if (matches.isNotEmpty()) {
            // 除錯用：印出找到了什麼
            println("🔎 Fuzzy search for '$query' found: ${matches.map { it.name }}")
            // 這裡回傳第一個匹配的結果
            return matches.first()
        }

        return null
    }
}