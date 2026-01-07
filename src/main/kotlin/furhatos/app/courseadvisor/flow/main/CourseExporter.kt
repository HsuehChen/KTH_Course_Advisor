package furhatos.app.courseadvisor.data

import java.io.File

object CourseExporter {

    fun export() {
        // 1. 取得資料 (存取這個變數時，CourseDatabase 就會自動執行 init 載入 JSON)
        val courses = CourseDatabase.allCourses

        if (courses.isEmpty()) {
            println("❌ 資料庫是空的！請檢查 src/main/resources/gui/course_all.json 是否存在。")
            return
        }

        // 2. 準備輸出檔案
        val file = File("course_dump.txt")
        val sb = StringBuilder()

        sb.append("=== Course List Dump (${courses.size} courses) ===\n")
        sb.append("Format: [CODE] Name\n")
        sb.append("=============================================\n\n")

        // 3. 寫入每一堂課的代碼與名稱
        courses.forEach { c ->
            sb.append("[${c.code}] ${c.name}\n")
        }

        // 4. 寫入硬碟
        file.writeText(sb.toString())
        println("✅ 匯出成功！請查看專案根目錄下的檔案：${file.absolutePath}")
    }
}

// 獨立的執行入口，不用啟動 Furhat 也能跑
fun main() {
    CourseExporter.export()
}