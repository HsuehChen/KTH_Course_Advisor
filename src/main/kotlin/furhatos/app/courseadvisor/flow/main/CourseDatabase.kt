package furhatos.app.courseadvisor.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// 用來解析 JSON 的資料結構 (簡化版，只抓需要的)
data class JsonCourseWrapper(val detailedInformation: DetailedInfo?)
data class DetailedInfo(val course: JsonCourse?)
data class JsonCourse(val courseCode: String?, val title: String?, val credits: Double?, val courseSyllabus: Syllabus?)
data class Syllabus(val goals: String?, val content: String?, val eligibility: String?, val examComments: String?)

// 這是我們程式內部使用的乾淨格式
data class CourseInfo(
    val code: String,
    val name: String,
    val credits: Double,
    val period: String = "P1", // 預設
    val availablePeriods: List<String> = listOf("P1") // 預設
)

object CourseDatabase {

    // 儲存所有解析後的課程物件
    var allCourses: List<CourseInfo> = emptyList()

    // 儲存給 NLU 使用的關鍵字清單 (包含課名和代碼)
    var nluKeywords: List<String> = emptyList()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        try {
            // 讀取 JSON 檔案 (假設檔案在 src/main/resources/assets/course_all.json)
            // 注意：在 Furhat 執行環境中，讀取 resources 的方式如下：
            val jsonString = this::class.java.getResource("/gui/course_all.json")?.readText()

            if (jsonString != null) {
                val listType = object : TypeToken<List<JsonCourseWrapper>>() {}.type
                val rawList: List<JsonCourseWrapper> = Gson().fromJson(jsonString, listType)

                // 轉換並過濾無效資料
                allCourses = rawList.mapNotNull { wrapper ->
                    val c = wrapper.detailedInformation?.course
                    if (c != null && c.courseCode != null && c.title != null) {
                        CourseInfo(
                            code = c.courseCode,
                            name = c.title,
                            credits = c.credits ?: 0.0,
                            period = "P1" // 這裡簡化處理，實際可加入你之前的解析邏輯
                        )
                    } else {
                        null
                    }
                }

                // 產生 NLU 關鍵字清單：我們把「課名」和「代碼」都加進去
                // 這樣用戶說 "DD2424" 或 "Deep Learning" 都能被辨識
                val names = allCourses.map { it.name }
                val codes = allCourses.map { it.code }
                nluKeywords = names + codes

                println("✅ Database loaded: ${allCourses.size} courses.")
                println("✅ NLU Keywords generated: ${nluKeywords.size} entries.")
            } else {
                println("❌ Error: course_all.json not found in resources/assets/")
            }
        } catch (e: Exception) {
            println("❌ Error loading JSON: ${e.message}")
            e.printStackTrace()
        }
    }

    // 給 NLU 呼叫的方法
    fun getNluList(): List<String> {
        return nluKeywords
    }

    // 搜尋方法
    fun findCourseByName(query: String): CourseInfo? {
        return allCourses.find {
            it.name.equals(query, ignoreCase = true) ||
                    it.code.equals(query, ignoreCase = true)
        }
    }
}