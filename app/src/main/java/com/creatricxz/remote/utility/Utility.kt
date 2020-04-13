package com.creatricxz.remote.utility

enum class InputType(var type: String) {
    REMOTE("RCU"),
    KEYBOARD("KEYBOARD"),
    MOUSE("MOUSE")
}

enum class PayloadType(var type: Int) {
    TEXT(0x01),
    BINARY(0x02),
}

enum class Remote(var value: Int) {
    PAYLOAD_TYPE(0x01),
    DATA_TYPE(0x02),
    RESERVED(0x00),
    KEYBOARD(0x01),
    MOUSE(0x02)
}

enum class Keyboard(var value: Int) {
    LENGTH(0x00000004),
    KEYBOARD_TYPE(0x01),
    KEYCODE(0x01)
}

enum class Mouse(var value: Int) {
    LENGTH(0x00000006),
    MOUSE_TYPE(-0x01),
    MOUSE_MOVE(0x01),
    MOUSE_LEFT_CLICK(0x00),
    MOUSE_RIGHT_CLICK(0x01)
}
