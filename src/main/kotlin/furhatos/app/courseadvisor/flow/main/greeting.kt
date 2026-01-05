package furhatos.app.courseadvisor.flow.main

import furhatos.app.courseadvisor.flow.Parent
import furhatos.app.courseadvisor.nlu.IAmDone
import furhatos.app.courseadvisor.nlu.StartPlanning
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.onResponse
import furhatos.flow.kotlin.state
import furhatos.gestures.Gestures
import furhatos.nlu.common.No
import furhatos.nlu.common.Yes
import furhatos.flow.kotlin.users


val Greeting: State = state(Parent) {
    onEntry {
        furhat.ask("Hey, This is your course advisor, My name is Brian. Should we start planning?")
    }

    onResponse<Yes> {
        furhat.gesture(Gestures.Oh)
        furhat.say("ok! Let's go!")
        goto(GuidedSearch)
    }

    onResponse<No> {
        furhat.say("Ok. let me know if you want to")
        goto(WaitingState)
    }

    onResponse<StartPlanning> {
        furhat.say("Okay, let's look at your schedule.")
        goto(GuidedSearch)
    }

    onResponse{
        furhat.ask("I don't understand. Are you ready to start planning?")
    }

}

val WaitingState = state {
    onEntry {
        furhat.gesture(Gestures.Smile)
        furhat.listen()
    }

    // 關鍵：只監聽「開始」相關的意圖
    onResponse<IAmDone> {
        furhat.attend(users.current)
        furhat.gesture(Gestures.Oh)
        furhat.say("Alright, Let's go!")
        goto(GuidedSearch)
    }

    onResponse<StartPlanning> {
        furhat.say("Okay, let's look at your schedule.")
        goto(GuidedSearch)
    }

    // Waiting for too long(2 min), actively check with user
    onTime(120000) {
        furhat.attend(users.current)
        furhat.say("If now isn't a good time, we can always chat later.")
        goto(Idle)
    }
}

