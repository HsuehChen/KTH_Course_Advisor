package furhatos.app.courseadvisor.flow

import furhatos.app.courseadvisor.flow.main.Idle
import furhatos.app.courseadvisor.flow.main.Greeting
import furhatos.app.courseadvisor.setting.DISTANCE_TO_ENGAGE
import furhatos.app.courseadvisor.setting.MAX_NUMBER_OF_USERS
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.state
import furhatos.flow.kotlin.users
import furhatos.skills.HostedGUI

val myGui = HostedGUI("CoursePlanning GUI", "src/main/resources/gui", port = 51234)

val Init: State = state {
    init {
        /** Set our default interaction parameters */
        users.setSimpleEngagementPolicy(DISTANCE_TO_ENGAGE, MAX_NUMBER_OF_USERS)
    }
    onEntry {
        /** start interaction */
        when {
            furhat.isVirtual() -> goto(Greeting) // Convenient to bypass the need for user when running Virtual Furhat
            users.hasAny() -> {
                furhat.attend(users.random)
                goto(Greeting)
            }
            else -> goto(Idle)
        }
    }

}
