package furhatos.app.courseadvisor.nlu

import furhatos.nlu.EnumEntity
import furhatos.nlu.Intent
import furhatos.util.Language
import furhatos.app.courseadvisor.data.CourseDatabase // 引入 Database

// 1. [修改] 使用帶有同義詞的清單
class CourseCode : EnumEntity() {
    override fun getEnum(lang: Language): List<String> {
        // 這會回傳 ["DT2212:DT 2212,D T 2 2 1 2...", ...]
        return CourseDatabase.getNluList()
    }
}

// EnumEntity 並動態載入清單
class CourseName : EnumEntity() {

    // 覆寫這個方法，Furhat 啟動時會呼叫這裡來建立辨識清單
    override fun getEnum(lang: Language): List<String> {
        // 從我們剛寫好的資料庫拿清單
        return CourseDatabase.getNluList()
    }
}


// Period Entity，用來辨識學期
class Period : EnumEntity() {
    override fun getEnum(lang: Language): List<String> {
        return listOf(
            "P1:Period 1,P1,one,1,autumn 1,first period",
            "P2:Period 2,P2,two,2,autumn 2,second period",
            "P3:Period 3,P3,three,3,spring 1,third period",
            "P4:Period 4,P4,four,4,spring 2,fourth period"
        )
    }
}

// Intent 定義保持不變，但現在 @courseName 會變得非常精準
class StartPlanning : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "I want to plan my courses",
            "Let's plan my schedule",
            "Start planning"
        )
    }
}

class AddCourse(
    var code: CourseCode? = null,
    var courseName: CourseName? = null
) : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Add @courseName",
            "I want to take @courseName",
            "I want to add @courseName",
            "Sign me up for @courseName",
            "Add @courseName to my schedule",
            "@courseName",

            "@code",
            "Add @code",
            "I want @code",
            "Course @code",
            "I want to add @code"
        )
    }
}

class RemoveCourse(
    var code: CourseCode? = null,
    var courseName: CourseName? = null
) : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Remove @courseName",
            "Delete @courseName",
            "Drop @courseName",
            "Remove course @courseName",
            "Delete code @code",
            "Remove @code",
            "Delete @code"
        )
    }
}

class CheckCart : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Check my schedule",
            "What courses do I have?"
        )
    }
}

// 1. 引導確認 (使用者說「好了」)
class IAmDone : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "I'm done",
            "I have selected it",
            "I found it",
            "Ready",
            "Okay",
            "Next",
            "Alright"
        )
    }
}

// 2. 清除特定 Period
class ClearPeriod(var period: Period? = null) : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Clear all courses in @period",
            "Remove everything from @period",
            "Clear @period",
            "Reset @period"
        )
    }
}

// 3. 清除全部
class ClearAll : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Clear all courses",
            "Remove everything",
            "Reset my schedule",
            "Empty cart",
            "Clear all"
        )
    }
}

// 4. 復原上一步
class UndoLast : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Undo",
            "Undo last action",
            "Go back",
            "I made a mistake",
            "Cancel that"
        )
    }
}

// Finishing planning schedule
class FinishPlanning : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "I am finished",
            "I'm finished",
            "I finished planning",
            "I'm done planning",
            "That's all",
            "That is it",
            "Stop",
            "Goodbye",
            "End session",
            "I don't want to add anything else",
            "No more courses"
        )
    }
}
