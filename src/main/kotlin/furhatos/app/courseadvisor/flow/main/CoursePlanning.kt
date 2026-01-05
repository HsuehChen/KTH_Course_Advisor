package furhatos.app.courseadvisor.flow.main

import furhatos.app.courseadvisor.nlu.*
import furhatos.app.courseadvisor.data.CourseDatabase
import furhatos.flow.kotlin.*
import furhatos.gestures.Gestures
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import com.google.gson.Gson
import java.io.File
import java.util.ArrayDeque

data class ScheduledCourse(
    val code: String,
    val name: String,
    val credits: Double,
    val period: String
)

var myCart = mutableListOf<ScheduledCourse>()
val historyStack = ArrayDeque<List<ScheduledCourse>>() // 歷史紀錄堆疊 (用於 Undo)

fun saveScheduleToDisk() {
    try {
        // 請確保路徑正確
        val file = File("src/main/resources/gui/my_schedule.json")
        val json = Gson().toJson(myCart)
        file.writeText(json)
        println("✅ Schedule saved to ${file.absolutePath}")
    } catch (e: Exception) {
        println("❌ Failed to save schedule: ${e.message}")
    }
}

// 儲存歷史紀錄 (在每次變更 myCart 前呼叫)
fun saveHistory() {
    // 存入一份目前的副本 (必須用 toList 深拷貝)
    historyStack.push(myCart.toList())
    // 限制堆疊大小為 10，避免記憶體過大
    if (historyStack.size > 10) {
        historyStack.removeLast()
    }
}

// 1. 引導搜尋狀態 (Turn-yielding)
val GuidedSearch: State = state {
    init {
        saveScheduleToDisk()
    }

    onEntry {
        if (myCart.isEmpty()) {
            // 引導使用者看螢幕
            furhat.say("Welcome. To help you plan, you can use the filters on the left side of the screen to find your programme.")
            delay(300)
            furhat.say("First, you can select a specific period to see what fits your schedule.")
            delay(300)
            furhat.say("You can filter for 7.5 credits for a standard course, or maybe something lighter if you prefer.")
            // 交還發話權 (Turn-yielding)
            furhat.ask("Select your track at the bottom of the filter, and tell me when you have selected your programme.")
        } else {
            // 如果已經有課，直接進入規劃模式
            goto(CoursePlanning)
        }
    }

    // 使用者說 "I'm done", "Ready"
    onResponse<IAmDone> {
        furhat.gesture(Gestures.Smile)
        furhat.say("Great! Now you should see the relevant courses.")
        goto(CoursePlanning)
    }

    // 如果使用者沒回答 "Done" 而是直接說 "Add Java"，我們也接受並轉發
    onResponse<AddCourse> {
        furhat.say("Ah, you found a course already!")
        call(CoursePlanning) // 轉發給主狀態
    }

    onNoResponse {
        furhat.ask("Please let me know when you've selected the filter.")
    }
}

// 2. 主要規劃狀態
val CoursePlanning: State = state {

    onEntry {
        if (myCart.isEmpty()) {
            // --- 購物車是空的 ---
            val emptyPrompts = listOf(
                "Tell me which course code or name you want to add.",
                "Which course should we add to your plan first?",
                "Let's get started. What is the name or code of the course?",
                "I'm ready. Please give me a course name or code.",
                "To begin, just tell me the course you are looking for."
            )
            furhat.say(emptyPrompts.random())

        } else {
            // --- 購物車已有東西 ---
            // 注意：這裡每一句都要包含 ${myCart.size} 變數
            val size = myCart.size
            val filledPrompts = listOf(
                "You have $size courses so far. What's next?",
                "That makes $size courses in your plan. Do you want to add another?",
                "Okay, currently you have $size courses. Anything else?",
                "Great, $size courses added. What other course do you have in mind?",
                "We have $size items in the list. Shall we add more?"
            )
            furhat.say(filledPrompts.random())
        }

        furhat.listen()
    }

    // --- 加選課程 ---
    onResponse<AddCourse> {
        // 因為是 EnumEntity，這裡取 .value 或 .toString()
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()

        if (rawName != null) {
            val foundCourse = CourseDatabase.findCourseByName(rawName)

            if (foundCourse != null) {
                if (myCart.any { item -> item.code == foundCourse.code }) {
                    furhat.gesture(Gestures.Surprise)
                    furhat.say("You already have ${foundCourse.name}.")
                } else {
                    // [關鍵] 修改前存檔 (Undo)
                    saveHistory()

                    val targetPeriod = foundCourse.availablePeriods.firstOrNull() ?: "P1"
                    val newItem = ScheduledCourse(
                        code = foundCourse.code,
                        name = foundCourse.name,
                        credits = foundCourse.credits,
                        period = targetPeriod
                    )

                    myCart.add(newItem)
                    saveScheduleToDisk() // 更新網頁

                    furhat.gesture(Gestures.Nod)
                    furhat.say("Added ${foundCourse.name} to $targetPeriod.")
                }
            } else {
                furhat.gesture(Gestures.BrowFrown)
                // 因為是 EnumEntity，理論上 database 一定有，除非同步出問題
                furhat.say("I heard $rawName, but I couldn't verify the details.")
            }
        } else {
            furhat.say("Which course?")
        }
        furhat.listen()
    }

    // --- 退選課程 ---
    onResponse<RemoveCourse> {
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()

        if (rawName != null) {
            val target = myCart.find { item ->
                item.name.equals(rawName, true) || item.code.equals(rawName, true)
            }

            if (target != null) {
                saveHistory() // [關鍵] 存檔 (Undo)

                myCart.remove(target)
                saveScheduleToDisk()

                furhat.gesture(Gestures.Nod)
                furhat.say("Removed ${target.name}.")
            } else {
                furhat.gesture(Gestures.Shake)
                furhat.say("You don't have ${rawName} in your schedule.")
            }
        } else {
            furhat.say("Which course to remove?")
        }
        furhat.listen()
    }

    // --- [新增] 清除特定 Period ---
    onResponse<ClearPeriod> {
        val p = it.intent.period?.value // 會拿到 "P1", "P2" 等
        if (p != null) {
            saveHistory()

            val initialSize = myCart.size
            myCart.removeAll { c -> c.period.equals(p, ignoreCase = true) }

            if (myCart.size < initialSize) {
                saveScheduleToDisk()
                furhat.say("Cleared all courses from Period $p.")
            } else {
                furhat.say("Period $p is already empty.")
            }
        } else {
            furhat.say("Which period?")
        }
        furhat.listen()
    }

    // --- [新增] 清除全部 ---
    onResponse<ClearAll> {
        if (myCart.isNotEmpty()) {
            saveHistory()
            myCart.clear()
            saveScheduleToDisk()
            furhat.gesture(Gestures.Nod)
            furhat.say("I've cleared your entire schedule.")
        } else {
            furhat.say("Your schedule is already empty.")
        }
        furhat.listen()
    }

    // --- [新增] 復原 (Undo) ---
    onResponse<UndoLast> {
        if (historyStack.isNotEmpty()) {
            // 從堆疊取出上一個狀態
            val previousState = historyStack.pop()
            // 還原 myCart
            myCart = previousState.toMutableList()
            // 更新網頁
            saveScheduleToDisk()

            furhat.gesture(Gestures.Nod)
            furhat.say("Undone. I've reverted the last change.")
        } else {
            furhat.say("There is nothing to undo.")
        }
        furhat.listen()
    }

    // --- 檢查課程 ---
    onResponse<CheckCart> {
        val totalCredits = myCart.sumOf { it.credits }
        if (myCart.isEmpty()) {
            furhat.say("You have no courses yet.")
        } else {
            val names = myCart.joinToString(", ") { it.code }
            furhat.say("You have: $names.")
        }
        furhat.listen()
    }

    onNoResponse { furhat.listen() }
}