package furhatos.app.courseadvisor.flow.main

import furhatos.app.courseadvisor.flow.Parent
import furhatos.app.courseadvisor.nlu.StartPlanning
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.onResponse
import furhatos.flow.kotlin.state
import furhatos.gestures.Gestures
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes


val Greeting: State = state(Parent) {
    onEntry {
        furhat.ask("Hey, This is your course advisor, My name is Brian. Should we start planning?")
    }

    onResponse<Yes> {
        furhat.gesture(Gestures.Oh)
        furhat.say("ok! Let's go!")
        goto(CoursePlanning)
    }

    onResponse<No> {
        furhat.say("Ok. let me know if you want to")
    }

    onResponse<StartPlanning> {
        furhat.say("Okay, let's look at your schedule.")
        goto(CoursePlanning)
    }

}

