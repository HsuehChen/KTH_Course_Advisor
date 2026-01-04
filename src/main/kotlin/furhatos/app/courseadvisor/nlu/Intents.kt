package furhatos.app.courseadvisor.nlu

import furhatos.nlu.EnumEntity
import furhatos.nlu.Intent
import furhatos.util.Language
import furhatos.app.courseadvisor.data.CourseDatabase // 引入 Database

// [重點修改] 改成 EnumEntity 並動態載入清單
class CourseName : EnumEntity() {

    // 覆寫這個方法，Furhat 啟動時會呼叫這裡來建立辨識清單
    override fun getEnum(lang: Language): List<String> {
        // 從我們剛寫好的資料庫拿清單
        return CourseDatabase.getNluList()
    }
}

// Intent 定義保持不變，但現在 @courseName 會變得非常精準
class StartPlanning : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "I want to plan my courses",
            "Let's plan my schedule"
        )
    }
}

class AddCourse(var courseName: CourseName? = null) : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Add @courseName",
            "I want to take @courseName",
            "Sign me up for @courseName",
            "Add @courseName to my schedule",
            "@courseName"
        )
    }
}

class RemoveCourse(var courseName: CourseName? = null) : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            "Remove @courseName",
            "Delete @courseName",
            "Drop @courseName"
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