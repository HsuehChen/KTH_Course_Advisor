package furhatos.app.courseadvisor.flow.main

import furhatos.app.courseadvisor.nlu.*
import furhatos.app.courseadvisor.data.CourseDatabase
import furhatos.app.courseadvisor.data.CourseInfo
import furhatos.flow.kotlin.*
import furhatos.gestures.Gestures
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import com.google.gson.Gson
import java.io.File
import java.util.ArrayDeque
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --- 資料結構 ---
data class ScheduledCourse(
    val code: String,
    val name: String,
    val credits: Double,
    val period: String
)

// [新增] 篩選條件資料結構
data class FilterSettings(
    var period: String = "",
    var credits: String = "",
    var programme: String = ""
)


// --- 全域變數 ---
var myCart = mutableListOf<ScheduledCourse>()
val historyStack = ArrayDeque<List<ScheduledCourse>>()

// 暫存變數
var tempCourseToAdd: CourseInfo? = null
var tempCourseToRemove: ScheduledCourse? = null

// 計數器
var dialogueTurns = 0
var failedAttempts = 0

// 當前篩選
var currentFilters = FilterSettings()

// --- 工具函式 ---

fun saveScheduleToDisk() {
    try {
        val file = File("src/main/resources/gui/my_schedule.json")
        val json = Gson().toJson(myCart)
        file.writeText(json)
        println("✅ Schedule saved to ${file.absolutePath}")
    } catch (e: Exception) {
        println("❌ Failed to save schedule: ${e.message}")
    }
}
// 寫入篩選設定到檔案
fun saveFiltersToDisk() {
    try {
        val file = File("src/main/resources/gui/filter_criteria.json")
        val json = Gson().toJson(currentFilters)
        file.writeText(json)
        println("✅ Filters saved: $json")
    } catch (e: Exception) {
        println("❌ Failed to save filters: ${e.message}")
    }
}

fun saveHistory() {
    historyStack.push(myCart.toList())
    if (historyStack.size > 10) historyStack.removeLast()
}

fun logInteraction(userSpeech: String) {
    dialogueTurns++
    try {
        val file = File("dialogue_logs.txt")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        file.appendText("[$timestamp] [OK] Turn: $dialogueTurns | User: $userSpeech\n")
        println("📝 Logged turn $dialogueTurns")
    } catch (e: Exception) {
        println("❌ Log Error: ${e.message}")
    }
}

fun logFailure(reason: String, userSpeech: String = "") {
    failedAttempts++
    try {
        val file = File("dialogue_logs.txt")
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val msg = if (userSpeech.isNotEmpty()) "User said: '$userSpeech'" else "Silence/No Response"
        file.appendText("[$timestamp] [FAIL] Count: $failedAttempts | Reason: $reason | $msg\n")
        println("⚠️ Logged Failure #$failedAttempts: $reason")
    } catch (e: Exception) {
        println("❌ Log Error: ${e.message}")
    }
}

// --- 狀態定義 ---

val GuidedSearch: State = state {
    init {
        currentFilters = FilterSettings()
        saveFiltersToDisk() // 清空前端顯示
        saveScheduleToDisk()
        try { File("dialogue_logs.txt").appendText("\n--- New Session ---\n") } catch (_: Exception){}
    }

    onEntry {
        if (myCart.isEmpty()) {
            furhat.ask("Welcome. To help you plan, you can use the filters on the left side of the screen, or should I help you filter the course?")
        } else {
            goto(CoursePlanning)
        }
    }

    onResponse<Yes> {
        furhat.say("Nice! Let me help you filter the courses.")
        goto(FilterAskPeriod)
    }
    onResponse<Sure> {
        furhat.say("Nice! Let me help you filter the courses.")
        goto(FilterAskPeriod)
    }

    onResponse<No> {
        furhat.say("Ok, First, you can select a specific period and credit to your course")
        delay(500)
        furhat.ask("Select your track at the bottom of the filter, and tell me when you have selected your programme.")
    }

    onResponse<IAmDone> {
        logInteraction(it.text)
        furhat.gesture(Gestures.Smile)
        furhat.say("Great! Now you should see the relevant courses.")
        goto(CoursePlanning)
    }

    onResponse<AddCourse> {
        logInteraction(it.text)
        furhat.say("Ah, you found a course already!")
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()
        if (rawName != null) {
            val foundCourse = CourseDatabase.findCourseByName(rawName)
            if (foundCourse != null) {
                if (myCart.any { item -> item.code == foundCourse.code }) {
                    furhat.say("You already have ${foundCourse.name}.")
                    goto(CoursePlanning)
                } else {
                    tempCourseToAdd = foundCourse
                    goto(ConfirmAddState)
                }
            } else {
                furhat.say("I couldn't find that course.")
                call(CoursePlanning)
            }
        } else {
            call(CoursePlanning)
        }
    }

    onResponse {
        logFailure("Unrecognized Intent in GuidedSearch", it.text)
        furhat.gesture(Gestures.BrowFrown)
        furhat.ask("I didn't quite get that. Just let me know when you are 'done' selecting filters.")
    }

    onNoResponse {
        logFailure("No Response in GuidedSearch")
        furhat.ask("Are you still there? Please tell me when you have selected the filter.")
    }
}

// 1. 問 Period (使用 TellPeriod)
val FilterAskPeriod: State = state {
    onEntry {
        furhat.ask("Which period? 1, 2, 3, or 4?")
    }

    // 專門監聽 Period 意圖
    onResponse<TellPeriod> {
        val p = it.intent.period?.value
        if (p != null) {
            currentFilters.period = p
            saveFiltersToDisk()
            furhat.gesture(Gestures.Nod)
            goto(FilterAskCredits)
        } else {
            // 有意圖但沒抓到值，轉給下方 text 處理
            reentry()
        }
    }

    onResponse<AnyOption> {
        currentFilters.period = ""
        saveFiltersToDisk()
        furhat.say("All periods.")
        goto(FilterAskCredits)
    }

    // Fallback: 直接檢查文字
    onResponse {
        val text = it.text.lowercase()
        var p: String? = null

        if (text.contains("1") || text.contains("one")) p = "P1"
        else if (text.contains("2") || text.contains("two")) p = "P2"
        else if (text.contains("3") || text.contains("three")) p = "P3"
        else if (text.contains("4") || text.contains("four")) p = "P4"
        else if (text.contains("any")) {
            currentFilters.period = ""
            saveFiltersToDisk()
            goto(FilterAskCredits)
            return@onResponse
        }

        if (p != null) {
            currentFilters.period = p
            saveFiltersToDisk()
            furhat.gesture(Gestures.Nod)
            goto(FilterAskCredits)
        } else {
            furhat.ask("Please say a number between 1 and 4.")
        }
    }
}

// 2. 問 Credits (使用 TellCredits)
val FilterAskCredits: State = state {
    onEntry {
        furhat.ask("How many credits? 7.5 or 6.0?")
    }

    onResponse<TellCredits> {
        val c = it.intent.credits?.value
        if (c != null) {
            currentFilters.credits = c
            saveFiltersToDisk()
            goto(FilterAskProgramme)
        } else {
            reentry()
        }
    }

    onResponse<AnyOption> {
        currentFilters.credits = ""
        saveFiltersToDisk()
        furhat.say("Any credits.")
        goto(FilterAskProgramme)
    }

    // Fallback
    onResponse {
        val text = it.text.lowercase()
        var c: String? = null

        if (text.contains("7.5") || text.contains("seven")) c = "7.5"
        else if (text.contains("6") || text.contains("six")) c = "6.0"
        else if (text.contains("9") || text.contains("nine")) c = "9.0"
        else if (text.contains("15") || text.contains("fifteen")) c = "15.0"
        else if (text.contains("30")) c = "30.0"
        else if (text.contains("any")) {
            currentFilters.credits = ""
            saveFiltersToDisk()
            goto(FilterAskProgramme)
            return@onResponse
        }

        if (c != null) {
            currentFilters.credits = c
            saveFiltersToDisk()
            goto(FilterAskProgramme)
        } else {
            furhat.ask("Please say 7.5, 6.0 or any.")
        }
    }
}

// 3. 問 Programme (使用 TellProgramme 或直接文字)
val FilterAskProgramme: State = state {
    onEntry {
        furhat.ask("Which programme track? Like Interactive Media Technology?")
    }

    onResponse<TellProgramme> {
        val prog = it.intent.programme?.text
        if (prog != null) {
            currentFilters.programme = prog
            saveFiltersToDisk()
            furhat.say("Filtering for $prog.")
            goto(CoursePlanning)
        } else {
            reentry()
        }
    }

    onResponse<AnyOption> {
        currentFilters.programme = ""
        saveFiltersToDisk()
        furhat.say("Showing all programmes.")
        goto(CoursePlanning)
    }

    // 針對 Programme，任何無法辨識的文字我們都假設是學程名稱
    onResponse {
        val text = it.text
        if (text.length > 2) { // 避免 "um", "ah"
            currentFilters.programme = text
            saveFiltersToDisk()
            furhat.say("Filtering for $text.")
            goto(CoursePlanning)
        } else {
            furhat.ask("Please tell me the programme name again.")
        }
    }
}

val CoursePlanning: State = state {

    val handleStop: TriggerRunner<*>.() -> Unit = {
        // 做最後一次存檔確認
        saveScheduleToDisk()

        furhat.gesture(Gestures.BigSmile)
        furhat.say("Alright, your schedule is saved. Good luck with your studies! Byebye!")

        // 進入 Idle 狀態 (結束 Skill 的互動流程)
        goto(Idle)
    }

    onEntry {
        if (myCart.isEmpty()) {
            val emptyPrompts = listOf(
                "Tell me which course code or name you want to add.",
                "Which course should we add to your plan first?",
                "I'm ready. Please give me a course name or code."
            )
            furhat.say(emptyPrompts.random())
        } else {
            val size = myCart.size
            val filledPrompts = listOf(
                "You have $size courses so far. What's next?",
                "That makes $size courses in your plan. Do you want to add another?",
                "We have $size items in the list. What course do you want to add another?"
            )
            furhat.say(filledPrompts.random())
        }
        furhat.listen()
    }

    // 使用者主動結束對話 ---
    onResponse<FinishPlanning>{
        logInteraction(it.text)
        handleStop()
    }
    // 使用者回答no need to plan
    onResponse<No>{
        logInteraction(it.text)
        handleStop()
    }

    onResponse<AddCourse> {
        logInteraction(it.text)
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()

        if (rawName != null) {
            val foundCourse = CourseDatabase.findCourseByName(rawName)

            if (foundCourse != null) {
                if (myCart.any { item -> item.code == foundCourse.code }) {
                    furhat.gesture(Gestures.Surprise)
                    furhat.say("You already have ${foundCourse.name}.")
                    furhat.listen()
                } else {
                    tempCourseToAdd = foundCourse
                    goto(CheckOverloadState)
                }
            } else {
                furhat.gesture(Gestures.BrowFrown)
                furhat.say("I heard $rawName, but I couldn't verify the details.")
                furhat.listen()
            }
        } else {
            furhat.say("Which course?")
            furhat.listen()
        }
    }

    onResponse<RemoveCourse> {
        logInteraction(it.text)
        val rawName = it.intent.courseName?.value ?: it.intent.courseName?.toString()

        if (rawName != null) {
            val target = myCart.find { item ->
                item.name.equals(rawName, true) || item.code.equals(rawName, true)
            }

            if (target != null) {
                tempCourseToRemove = target
                goto(ConfirmRemoveState)
            } else {
                furhat.gesture(Gestures.Shake)
                furhat.say("You don't have ${rawName} in your schedule.")
                furhat.listen()
            }
        } else {
            furhat.say("Which course to remove?")
            furhat.listen()
        }
    }

    onResponse<ClearPeriod> {
        logInteraction(it.text)
        val p = it.intent.period?.value
        if (p != null) {
            saveHistory()
            val initialSize = myCart.size
            myCart.removeAll { c -> c.period.equals(p, ignoreCase = true) }

            if (myCart.size < initialSize) {
                saveScheduleToDisk()
                furhat.say("Cleared all courses from $p.")
            } else {
                furhat.say("$p is already empty.")
            }
        }
        furhat.listen()
    }

    onResponse<ClearAll> {
        logInteraction(it.text)
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

    onResponse<UndoLast> {
        logInteraction(it.text)
        if (historyStack.isNotEmpty()) {
            val previousState = historyStack.pop()
            myCart = previousState.toMutableList()
            saveScheduleToDisk()
            furhat.gesture(Gestures.Nod)
            furhat.say("Undone. I've reverted the last change.")
        } else {
            furhat.say("There is nothing to undo.")
        }
        furhat.listen()
    }

    onResponse<CheckCart> {
        logInteraction(it.text)
        val names = myCart.joinToString(", ") { it.code }
        furhat.say("You have: $names.")
        furhat.listen()
    }

    // Catch-all
    onResponse {
        logFailure("Unrecognized Intent", it.text)
        furhat.gesture(Gestures.BrowFrown)
        furhat.say("Sorry, I didn't catch that.")
        furhat.listen()
    }

    onNoResponse {
        logFailure("No Response")
        furhat.listen()
    }
}

// --- [新增] 中介狀態：檢查學分是否超標 ---
val CheckOverloadState: State = state {
    onEntry {
        val c = tempCourseToAdd
        if (c != null) {
            val targetPeriod = c.availablePeriods.firstOrNull() ?: "P1"
            val currentCredits = myCart.filter { it.period == targetPeriod }.sumOf { it.credits }
            val newTotal = currentCredits + c.credits

            if (newTotal > 15.0) {
                goto(OverloadWarningState)
            } else {
                goto(ConfirmAddState)
            }
        } else {
            goto(CoursePlanning)
        }
    }
}

// --- [新增] 超修警告狀態 ---
val OverloadWarningState: State = state {
    onEntry {
        val c = tempCourseToAdd!!
        val p = c.availablePeriods.firstOrNull() ?: "P1"
        furhat.gesture(Gestures.Oh)
        furhat.ask("Wait, adding this course will exceed 15 credits in $p. That is a heavy workload. Are you sure you want to add it?")
    }

    onResponse<Yes> {
        logInteraction(it.text)

        // [關鍵修改] 這裡直接執行加選動作，不跳去 ConfirmAddState
        val c = tempCourseToAdd
        if (c != null) {
            saveHistory()
            val p = c.availablePeriods.firstOrNull() ?: "P1"
            val newItem = ScheduledCourse(c.code, c.name, c.credits, p)
            myCart.add(newItem)
            saveScheduleToDisk()

            furhat.gesture(Gestures.Nod)
            // 簡單確認
            furhat.say("Okay, I have added it to your schedule.")
        }

        tempCourseToAdd = null
        goto(CoursePlanning)
    }

    onResponse<No> {
        logInteraction(it.text)
        furhat.gesture(Gestures.Nod)
        furhat.say("Wise choice. Let's find something else.")
        tempCourseToAdd = null
        goto(CoursePlanning)
    }

    onResponse {
        logFailure("Unrecognized in OverloadWarning", it.text)
        furhat.ask("Please just say yes or no. Do you still want to add it despite the overload?")
    }
}

// --- 確認加選狀態 (一般流程) ---
val ConfirmAddState: State = state {
    onEntry {
        val c = tempCourseToAdd
        if (c != null) {
            val p = c.availablePeriods.firstOrNull() ?: "P1"
            val credits = c.credits
            furhat.ask("I found ${c.name}. It is $credits credits and runs in $p. Do you want to add it?")
        } else {
            goto(CoursePlanning)
        }
    }

    onResponse<Yes> {
        logInteraction(it.text)
        val c = tempCourseToAdd
        if (c != null) {
            saveHistory()
            val p = c.availablePeriods.firstOrNull() ?: "P1"
            val newItem = ScheduledCourse(c.code, c.name, c.credits, p)
            myCart.add(newItem)
            saveScheduleToDisk()
            furhat.gesture(Gestures.Nod)
            furhat.say("Okay, added.")
        }
        tempCourseToAdd = null
        goto(CoursePlanning)
    }

    onResponse<No> {
        logInteraction(it.text)
        furhat.gesture(Gestures.Smile)
        furhat.say("Okay, cancelled.")
        tempCourseToAdd = null
        goto(CoursePlanning)
    }

    onResponse<FinishPlanning> {
        logInteraction(it.text)
        furhat.say("Okay, let's stop here. Byebye!")
        goto(Idle)
    }

    onResponse {
        logFailure("Unrecognized during Confirmation", it.text)
        furhat.say("I'll take that as a no.")
        tempCourseToAdd = null
        goto(CoursePlanning)
    }
}

// --- 確認退選狀態 ---
val ConfirmRemoveState: State = state {
    onEntry {
        val c = tempCourseToRemove
        if (c != null) {
            furhat.ask("Are you sure you want to remove ${c.name}?")
        } else {
            goto(CoursePlanning)
        }
    }

    onResponse<Yes> {
        logInteraction(it.text)
        val c = tempCourseToRemove
        if (c != null) {
            saveHistory()
            myCart.remove(c)
            saveScheduleToDisk()
            furhat.gesture(Gestures.Nod)
            furhat.say("Okay, removed.")
        }
        tempCourseToRemove = null
        goto(CoursePlanning)
    }

    onResponse<No> {
        logInteraction(it.text)
        furhat.say("Okay, keeping it.")
        tempCourseToRemove = null
        goto(CoursePlanning)
    }

    onResponse<FinishPlanning> {
        logInteraction(it.text)
        furhat.say("Okay, let's stop. Byebye!")
        goto(Idle)
    }

    onResponse {
        logFailure("Unrecognized during Confirmation", it.text)
        furhat.say("Cancelled.")
        tempCourseToRemove = null
        goto(CoursePlanning)
    }
}