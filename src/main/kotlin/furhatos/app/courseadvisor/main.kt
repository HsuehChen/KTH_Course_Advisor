package furhatos.app.courseadvisor

import furhatos.app.courseadvisor.flow.Init
import furhatos.flow.kotlin.Flow
import furhatos.skills.Skill



class CourseadvisorSkill : Skill() {
    override fun start() {
        Flow().run(Init)
    }
}

fun main(args: Array<String>) {
    Skill.main(args)
}
