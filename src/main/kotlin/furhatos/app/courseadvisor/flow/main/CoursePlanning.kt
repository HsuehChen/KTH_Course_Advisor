package furhatos.app.courseadvisor.flow.main

import furhatos.app.courseadvisor.nlu.*
import furhatos.app.courseadvisor.data.CourseDatabase
import furhatos.flow.kotlin.*
import furhatos.gestures.Gestures
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import com.google.gson.Gson // 務必確認有 import Gson
import java.io.File // 用來寫檔案

// 資料結構
data class ScheduledCourse(
    val code: String,
    val name: String,
    val credits: Double,
    val period: String
)

// 全域變數
val myCart = mutableListOf<ScheduledCourse>()

// 這個函式會把 myCart 寫入到 src/main/resources/assets/my_schedule.json
fun saveScheduleToDisk() {
    try {
        // 設定檔案路徑 (這是專案開發時的標準路徑)
        // 如果您的專案路徑結構不同，可能需要調整這裡
        val file = File("src/main/resources/gui/my_schedule.json")

        // 轉成 JSON 字串
        val json = Gson().toJson(myCart)

        // 寫入檔案
        file.writeText(json)

        println("✅ Schedule saved to ${file.absolutePath}")
    } catch (e: Exception) {
        println("❌ Failed to save schedule: ${e.message}")
    }
}

val CoursePlanning: State = state {

    init {
        // 剛開始時先存一次空的（或目前的）狀態，確保檔案存在
        saveScheduleToDisk()
    }

    onEntry {
        if (myCart.isEmpty()) {
            furhat.say("Your schedule is currently empty. Tell me which course you want to add.")
        } else {
            furhat.say("You have ${myCart.size} courses in your plan. What next?")
        }
        furhat.listen()
    }

    // --- 加選課程 ---
    onResponse<AddCourse> {
        val rawName = it.intent.courseName?.text

        if (rawName != null) {
            val foundCourse = CourseDatabase.findCourseByName(rawName)

            if (foundCourse != null) {
                // 檢查重複
                if (myCart.any { item -> item.code == foundCourse.code }) {
                    furhat.gesture(Gestures.Surprise)
                    furhat.say("You already have ${foundCourse.name}.")
                } else {
                    // [關鍵修正]：動態取得學期與學分
                    // 邏輯：優先選該課程的第一個可用學期 (例如它是 P3 的課，就會自動選 P3)
                    val targetPeriod = foundCourse.availablePeriods.firstOrNull() ?: "P1"
                    val actualCredits = foundCourse.credits

                    val newItem = ScheduledCourse(
                        code = foundCourse.code,
                        name = foundCourse.name,
                        credits = actualCredits, // 使用真實學分
                        period = targetPeriod    // 使用真實學期
                    )

                    myCart.add(newItem)
                    saveScheduleToDisk()

                    furhat.gesture(Gestures.Nod)
                    // 讓 Furhat 說出具體的資訊
                    furhat.say("Added ${foundCourse.name}. It is $actualCredits credits and runs in period $targetPeriod.")
                }
            } else {
                furhat.gesture(Gestures.BrowFrown)
                furhat.say("I heard $rawName, but I couldn't find it.")
            }
        } else {
            furhat.say("Which course?")
        }
        furhat.listen()
    }

    // --- 退選課程 ---
    onResponse<RemoveCourse> {
        // 1. 取得用戶說的課程名稱 (從 EnumEntity 取值)
        // 因為是 EnumEntity，它回傳的通常是 CourseDatabase 裡有的標準名稱或代碼
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()

        if (rawName != null) {
            // 2. 在「購物車 (myCart)」裡面找這堂課
            // 我們比對名稱或代碼 (忽略大小寫)
            val target = myCart.find { item ->
                item.name.equals(rawName, ignoreCase = true) ||
                        item.code.equals(rawName, ignoreCase = true)
            }

            if (target != null) {
                // 3. 執行刪除
                myCart.remove(target)

                // 4. [關鍵] 存檔更新網頁
                saveScheduleToDisk()

                furhat.gesture(Gestures.Nod)
                furhat.say("Okay, I have removed ${target.name} from your schedule.")
            } else {
                // 5. 購物車裡沒這門課 (雖然它是有效的課程名稱，但沒被加選)
                furhat.gesture(Gestures.Shake)
                furhat.say("You don't have ${rawName} in your schedule right now.")
            }
        } else {
            // 6. 沒聽清楚要刪哪一門
            furhat.say("Which course would you like to remove?")
        }
        furhat.listen()
    }

    // --- 檢查課程 ---
    onResponse<CheckCart> {
        val totalCredits = myCart.sumOf { it.credits }
        if (myCart.isEmpty()) {
            goto(AskToAddCourse)
        } else {
            val names = myCart.joinToString(", ") { it.code }
            furhat.say("You have: $names.")
        }
        furhat.listen()
    }

    onNoResponse { furhat.listen() }
}

val AskToAddCourse: State = state {
    onEntry { furhat.ask("Your cart is empty. Check the screen?") }
    onResponse<Yes> {
        furhat.say("Great.")
        goto(CoursePlanning)
    }
    onResponse<No> {
        furhat.say("Okay.")
        goto(CoursePlanning)
    }
    onResponse<AddCourse> { reentry() }
}