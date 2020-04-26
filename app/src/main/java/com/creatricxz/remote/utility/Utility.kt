package com.creatricxz.remote.utility

enum class PayloadType(var type: Int) {
    RCU(0x01),
    ACU(0x02),
}

enum class PayloadDataType(var type: Int) {
    TEXT(0x01),
    BINARY(0x02),
}

enum class InputPayloadType(var type: Int) {
    KEYBOARD(0x01),
    MOUSE(0x02)
}

object PayloadLength{
    var KEYBOARD = 0x04
    var MOUSE = 0x04
    var HEADER = 0x08
}

enum class MouseAction(var value: Int) {
    MOVE(-0x01),
    LEFT_CLICK(0x01),
    RIGHT_CLICK(0x02)
}
