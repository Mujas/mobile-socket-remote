package com.creatricxz.remote

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.KeyEvent.*
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration.getTapTimeout
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.creatricxz.remote.settings.SettingsActivity
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder


open class MainActivity : AppCompatActivity() {

    private val LOG_TAG = "Touchpad"

    private val CANCEL_ID = Menu.FIRST

    // Options menu.
    private val CONNECT_ID = CANCEL_ID + 1
    private val DISCONNECT_ID = CONNECT_ID + 1
    private val ADD_FAVORITE_ID = DISCONNECT_ID + 1
    private val PREFERENCES_ID = ADD_FAVORITE_ID + 1
    private val EXIT_ID = PREFERENCES_ID + 1

    // Context (server) menu.
    private val FAVORITES_ID = CANCEL_ID + 1
    private val FIND_SERVERS_ID = FAVORITES_ID + 1
    private val SERVER_CUSTOM_ID = FIND_SERVERS_ID + 1
    private val SERVER_FOUND_ID = Menu.FIRST
    private val SERVER_FAVORITE_ID = Menu.FIRST + 1

    protected val KeepAlive = 2000
    private val DefaultPort = 2999
    private val MaxServers = 9

    // Current preferences.
    protected var Port: Short = 0
    protected var Sensitivity = 0f
    protected var MultitouchMode = 0
    protected var Timeout = 0
    protected var EnableScrollBar = false
    protected var ScrollBarWidth = 0
    protected var EnableSystem = false

    // State.
    protected var timer: Handler = Handler()
    var mWebSocket: WebSocket? = null

    // Activity lifecycle.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.activity_main)
        // Set touchpad events.
        touchpad!!.setOnTouchListener(mTouchListener)
        // Set keyboard events.
        keyboard!!.setOnClickListener(mKeyboardListener)
        keyboard!!.setOnKeyListener(mKeyListener)
        key_shift!!.setOnCheckedChangeListener { compoundButton, checked ->
            if (checked) sendKeyDown(
                KEYCODE_SHIFT_LEFT.toShort(),
                0.toShort()
            ) else sendKeyUp(KEYCODE_SHIFT_LEFT.toShort(), 0.toShort())
        }
        key_ctrl!!.setOnCheckedChangeListener { compoundButton, checked ->
            if (checked) sendKeyDown(
                113.toShort(),
                0.toShort()
            ) else sendKeyUp(113.toShort(), 0.toShort())
        }
        key_alt!!.setOnCheckedChangeListener { compoundButton, checked ->
            if (checked) sendKeyDown(
                KEYCODE_ALT_LEFT.toShort(),
                0.toShort()
            ) else sendKeyUp(KEYCODE_ALT_LEFT.toShort(), 0.toShort())
        }


        // Set mouse button events.
        button0!!.setOnCheckedChangeListener(mButton0ToggleListener)
        button0!!.setOnLongClickListener(mButton0ClickListener)
        button1!!.setOnCheckedChangeListener(mButton1ToggleListener)
        button1!!.setOnLongClickListener(mButton1ClickListener)
        // Set media button events.

        prevtrack.setOnClickListener {
            sendKeyPress(KEYCODE_MEDIA_PREVIOUS.toShort(), 0.toShort())
        }

        playpause.setOnClickListener {
            sendKeyPress(KEYCODE_MEDIA_PLAY_PAUSE.toShort(), 0.toShort())
        }

        nexttrack.setOnClickListener {
            sendKeyPress(KEYCODE_MEDIA_NEXT.toShort(), 0.toShort())
        }

        stop.setOnClickListener {
            sendKeyPress(KEYCODE_MEDIA_STOP.toShort(), 0.toShort())
        }

        volumemute.setOnClickListener {
            sendKeyPress(KEYCODE_MUTE.toShort(), 0.toShort())
        }

        volumedown.setOnClickListener {
            for (i in 0..2) sendKeyPress(
                KEYCODE_VOLUME_DOWN.toShort(),
                0.toShort()
            )
        }
        volumedown.setOnLongClickListener {
            sendKeyPress(KEYCODE_VOLUME_DOWN.toShort(), 0.toShort())
            true
        }

        volumeup.setOnClickListener {
            for (i in 0..2) sendKeyPress(
                KEYCODE_VOLUME_UP.toShort(),
                0.toShort()
            )
        }

        volumeup.setOnLongClickListener {
            sendKeyPress(KEYCODE_VOLUME_UP.toShort(), 0.toShort())
            true
        }

        // Set browser button events.
        browsehome.setOnClickListener {
            sendKeyPress(KEYCODE_HOME.toShort(), 0.toShort())
        }

        browseforward.setOnClickListener {
            sendKeyPress(125.toShort(), 0.toShort())
        }

        browseback.setOnClickListener {
            sendKeyPress(KEYCODE_BACK.toShort(), 0.toShort())
        }
        // Set up preferences.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        // If there is no server to reconnect, set the background to bad.
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val to = preferences.getString("Server", null)
        if (to == null) touchpad.setImageResource(R.drawable.background_bad)
    }

    override fun onResume() {
        super.onResume()
        // Restore settings.
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        Port = try {
            preferences.getString(
                "Port",
                Integer.toString(DefaultPort)
            )!!.toInt().toShort()
        } catch (ex: NumberFormatException) {
            DefaultPort.toShort()
        }
        Sensitivity = preferences.getInt("Sensitivity", 50).toFloat() / 25.0f + 0.1f
        MultitouchMode = try {
            preferences.getString("MultitouchMode", "0")!!.toInt()
        } catch (ex: NumberFormatException) {
            0
        }
        Timeout = preferences.getInt("Timeout", 500) + 1
        EnableScrollBar =
            preferences.getBoolean("EnableScrollBar", preferences.getBoolean("EnableScroll", true))
        ScrollBarWidth = preferences.getInt("ScrollBarWidth", 20)
        EnableSystem = preferences.getBoolean("EnableSystem", true)
        val EnableMouseButtons = preferences.getBoolean("EnableMouseButtons", true)
        val EnableModifiers = preferences.getBoolean("EnableModifiers", false)
        var Toolbar = 0
        try {
            Toolbar = preferences.getString("Toolbar", "0")!!.toInt()
        } catch (ex: NumberFormatException) {
        }
        // Show/hide the mouse buttons.
        if (EnableMouseButtons) mousebuttons!!.visibility =
            View.VISIBLE else mousebuttons!!.visibility =
            View.GONE
        // Show/hide the modifier keys.
        if (EnableModifiers) modifiers.setVisibility(View.VISIBLE) else modifiers.setVisibility(View.GONE)
        // Show/hide media toolbar.
        if (Toolbar == 1) media!!.visibility = View.VISIBLE else media!!.visibility = View.GONE
        // Show/hide browser toolbar.
        if (Toolbar == 2) browser.setVisibility(View.VISIBLE) else browser.setVisibility(View.GONE)
        timer.postDelayed(mKeepAliveListener, KeepAlive.toLong())
    }

    override fun onPause() {
        timer.removeCallbacks(mKeepAliveListener)
        //disconnect(true)
        super.onPause()
    }

    // Mouse actions.
    open inner class Action {
        protected var downX = 0f
        protected var downY = 0f
        protected var oldX = 0f
        protected var oldY = 0f
        protected var pointerId = 0
        protected var downTime: Long = 0
        protected var moving = false
        fun isClick(e: MotionEvent): Boolean {
            val vc = ViewConfiguration.get(touchpad.context)
            val index = e.findPointerIndex(pointerId)
            return Math.abs(e.getX(index) - downX) < vc.scaledTouchSlop && Math.abs(e.getY(index) - downY) < vc.scaledTouchSlop && e.eventTime - downTime < getTapTimeout()
        }

        open fun onDown(e: MotionEvent): Boolean {
            pointerId = e.getPointerId(0)
            val index = e.findPointerIndex(pointerId)
            downX = e.getX(index)
            oldX = downX
            downY = e.getY(index)
            oldY = downY
            downTime = e.eventTime
            moving = false
            return true
        }

        open fun onUp(e: MotionEvent): Boolean {
            if (isClick(e)) onClick()
            return true
        }

        open fun acceptMove(e: MotionEvent?): Boolean {
            return true
        }

        open fun onMove(e: MotionEvent): Boolean {
            if (!acceptMove(e)) return false
            val index = e.findPointerIndex(pointerId)
            val X = e.getX(index)
            val Y = e.getY(index)
            if (moving) onMoveDelta(
                (X - oldX) * Sensitivity,
                (Y - oldY) * Sensitivity
            ) else moving = true
            oldX = X
            oldY = Y
            return true
        }

        open fun cancel(e: MotionEvent?): Boolean {
            return false
        }

        open fun onMoveDelta(dx: Float, dy: Float) {}
        open fun onClick() {}
    }


    inner class MoveAction : Action() {

        override fun onMoveDelta(dx: Float, dy: Float) {
            sendMove(dx, dy)
        }

        override fun onClick() {
            if (button0.isChecked) button0.toggle()
            sendClick(0)
        }

        override fun cancel(e: MotionEvent?): Boolean {
            return true
        }
    }

    open inner class ScrollAction : Action() {
        var time: Long = 0
        override fun onDown(e: MotionEvent): Boolean {
            time = e.eventTime
            return super.onDown(e)
        }

        override fun acceptMove(e: MotionEvent?): Boolean {
            if (e!!.eventTime + 200 < time)
                return false
            time = e.eventTime
            return true
        }

        override fun onMoveDelta(dx: Float, dy: Float) {
            sendScroll(-2.0f * dy)
        }
    }

    inner class ScrollAction2 : ScrollAction() {
        override fun onMoveDelta(dx: Float, dy: Float) {
            sendScroll2(dx, -2.0f * dy)
        }

        override fun onClick() {
            if (button1.isChecked) button1.toggle()
            sendClick(1)
        }
    }

    inner class DragAction : Action() {
        protected var drag = false
        override fun onMove(e: MotionEvent): Boolean {
            if (!drag && !isClick(e)) {
                sendDown(0)
                drag = true
            }
            return super.onMove(e)
        }

        override fun onUp(e: MotionEvent): Boolean {
            if (drag) sendUp(0)
            return super.onUp(e)
        }

        override fun onMoveDelta(dx: Float, dy: Float) {
            sendMove(dx, dy)
        }

        override fun onClick() {
            sendClick(1)
        }
    }

    // Mouse listeners.
    var mTouchListener: OnTouchListener = object : OnTouchListener {
        protected var action: Action? = null
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            return when (e.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (action != null) if (!action!!.cancel(e)) return true
                    action = null
                    // If this is a multitouch action, check the multitouch mode.
                    if (e.pointerCount >= 2) {
                        when (MultitouchMode) {
                            1 -> action = DragAction()
                            2 -> action = ScrollAction2()
                        }
                    }
                    // If the action is still null, check if it is a scroll action.
                    if (action == null && EnableScrollBar && (e.edgeFlags and MotionEvent.EDGE_RIGHT != 0 || e.x > v.width - ScrollBarWidth)) action =
                        ScrollAction()
                    // If the action is still null, it is a plain move action.
                    if (action == null) action = MoveAction()
                    if (action != null) action!!.onDown(e) else true
                }
                MotionEvent.ACTION_DOWN -> {
                    action = null
                    if (e.pointerCount >= 2) {
                        when (MultitouchMode) {
                            1 -> action = DragAction()
                            2 -> action = ScrollAction2()
                        }
                    }
                    if (action == null && EnableScrollBar && (e.edgeFlags and MotionEvent.EDGE_RIGHT != 0 || e.x > v.width - ScrollBarWidth)) action =
                        ScrollAction()
                    if (action == null) action = MoveAction()
                    if (action != null) action!!.onDown(e) else true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (action != null) action!!.onUp(e)
                    action = null
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (action != null) action!!.onMove(e) else true
                }
                else -> false
            }
        }
    }
    var mButton0ToggleListener: CompoundButton.OnCheckedChangeListener =
        object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(cb: CompoundButton?, on: Boolean) {
                if (on) sendDown(0) else sendUp(0)
            }
        }
    var mButton0ClickListener = OnLongClickListener {
        if (button0!!.isChecked) button0!!.toggle()
        sendClick(0)
        sendClick(0)
        true
    }
    var mButton1ToggleListener: CompoundButton.OnCheckedChangeListener =
        object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(cb: CompoundButton?, on: Boolean) {
                if (on) sendDown(1) else sendUp(1)
            }
        }
    var mButton1ClickListener = OnLongClickListener {
        if (button1!!.isChecked) button1!!.toggle()
        sendClick(1)
        true
    }

    // Keyboard listener.
    var mKeyboardListener: View.OnClickListener = object : View.OnClickListener {
        override fun onClick(v: View?) {
            val imm: InputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(keyboard, InputMethodManager.SHOW_FORCED)
        }
    }
    var mKeyListener: View.OnKeyListener = object : View.OnKeyListener {
        override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
            if (event.action === ACTION_DOWN) return onKeyDown(keyCode, event)
            if (event.action === ACTION_MULTIPLE) return onKeyMultiple(
                keyCode,
                event.repeatCount,
                event
            )
            return if (event.action === ACTION_UP) onKeyUp(keyCode, event) else false
        }
    }

    // Timer listener.
    var mKeepAliveListener: Runnable = object : Runnable {
        override fun run() {
            //sendNull()
            timer.postDelayed(this, KeepAlive.toLong())
        }
    }

    // Keyboard events.
    fun ignoreKeyEvent(event: KeyEvent): Boolean {
        if (event.isSystem && !EnableSystem) return true
        when (event.keyCode) {
            KEYCODE_MENU, KEYCODE_HOME, KEYCODE_POWER -> return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ignoreKeyEvent(event)) return super.onKeyDown(keyCode, event)
        val c = event.unicodeChar
        if (c == 0 || Character.isISOControl(c) || key_shift!!.isChecked || key_ctrl.isChecked() || key_alt.isChecked()) sendKeyPress(
            event.keyCode.toShort(),
            event.metaState.toShort()
        ) else sendChar(c.toChar())
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        if (keyCode == KEYCODE_UNKNOWN) {
            val s = event.characters
            for (i in 0 until s.length) sendChar(s[i])
        } else {
            for (i in 0 until repeatCount) onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (ignoreKeyEvent(event)) super.onKeyUp(keyCode, event) else true
    }

    // Connection management.
    protected fun isConnected(): Boolean {
        reconnect()
        return mWebSocket != null
    }

    protected fun reconnect() {
        if (mWebSocket == null) { // Reconnect to default server.
            val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val to = preferences.getString("Server", null)
            val password = preferences.getInt("Password", 0)
            if (to != null) connect(to, password)
        }
    }

    protected fun connect(to: String): Boolean {
        return connect(to, 0)
    }

    protected fun connect(to: String, password: Int): Boolean {
        return connectIOSocket(to)
    }

    fun connectIOSocket(to: String): Boolean {
        Observable.just("Hello World")
            .subscribeOn(Schedulers.newThread())
            .observeOn(Schedulers.newThread())
            .subscribe { smth ->
                testWSS(to) // !!! must be not on the MainThread
            }

        return true
    }

    private fun testWSS(to : String) {
        // Create a WebSocket with a socket connection timeout value.
        mWebSocket = WebSocketFactory().createSocket("ws://"+ to, 5000)

        // Register a listener to receive WebSocket events.
        mWebSocket!!.addListener(object : WebSocketAdapter() {
            override fun onTextMessage(websocket: WebSocket?, text: String?) {
                super.onTextMessage(websocket, text)
                Log.e("WSS", text)
            }

            override fun onCloseFrame(websocket: WebSocket?, frame: WebSocketFrame?) {
                super.onCloseFrame(websocket, frame)
                Log.e("WSS", "closing socket")
            }

            override fun onConnected(
                websocket: WebSocket?,
                headers: MutableMap<String, MutableList<String>>?
            ) {
                super.onConnected(websocket, headers)
                Log.e("WSS", "Connecting socket")
                touchpad.setImageResource(R.drawable.background)
            }
        })

        mWebSocket!!.connect()
        //mWebSocket!!.sendText("hello !")
        //ws.disconnect()
    }

    protected fun disconnect() {
        disconnect(false)
    }

    protected fun disconnect(reconnect: Boolean) { // Clear default server.
        if (!reconnect) {
            val editor: Editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
            editor.remove("Server")
            editor.remove("Password")
            editor.commit()
            Log.i(LOG_TAG, "Cleared connection info.")
        }
        sendDisconnect(reconnect)
        try {
            mWebSocket!!.disconnect()
        } catch (e: Exception) {
        }
        mWebSocket = null
        if (!reconnect) touchpad.setImageResource(R.drawable.background_bad)
    }

    // Context menu.
    @Throws(Exception::class)
    protected fun findServers(menu: Menu) { // Broadcast ping to look for servers.
        val beacon = DatagramSocket(null)
        beacon.setBroadcast(true)
        beacon.setSoTimeout(Timeout)
        val broadcast: InetAddress = getBroadcastAddress()
        val buffer = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00)
        beacon.send(DatagramPacket(buffer, 5, broadcast, Port.toInt()))
        if (Port.toInt() != DefaultPort) beacon.send(
            DatagramPacket(
                buffer,
                5,
                broadcast,
                DefaultPort
            )
        )
        try { // Add each ack to the menu.
            for (i in 0 until MaxServers) {
                val port = ByteArray(5)
                val ack = DatagramPacket(port, 5)
                beacon.receive(ack)
                val parser: ByteBuffer = ByteBuffer.wrap(port)
                if (parser.get() === 0x03.toByte()) {
                    val addr: String =
                        ack.getAddress().toString().substring(1).toString() + ":" + parser.getShort()
                    menu.add(SERVER_FOUND_ID, 0, 0, addr).setShortcut(
                        (i + '1'.toInt()).toChar(),
                        (i + 'a'.toInt()).toChar()
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
        }
    }

    protected fun findFavorite(server: String?): Int {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        for (i in 0 until MaxServers) {
            val addr = preferences.getString("Favorite$i", null)
            if (addr == null && server == null ||
                addr != null && server != null && addr == server
            ) return i
        }
        return -1
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu!!.clear()
        //menu.addSubMenu(0, FAVORITES_ID, 0, R.string.favoriteservers).setHeaderTitle(R.string.servers);
        menu.addSubMenu(0, FIND_SERVERS_ID, 1, R.string.findservers)
            .setHeaderTitle(R.string.servers)
        menu.add(0, SERVER_CUSTOM_ID, 2, R.string.customserver).setShortcut('1', 'c')
        menu.add(0, CANCEL_ID, 3, R.string.cancel).setShortcut('2', 'x')
        return super.onCreateOptionsMenu(menu)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View?,
        menuInfo: ContextMenuInfo?
    ) {
        menu.clear()
        menu.setHeaderTitle(R.string.servers)
        //menu.addSubMenu(0, FAVORITES_ID, 0, R.string.favoriteservers).setHeaderTitle(R.string.servers);
        menu.addSubMenu(0, FIND_SERVERS_ID, 1, R.string.findservers)
            .setHeaderTitle(R.string.servers)
        menu.add(0, SERVER_CUSTOM_ID, 2, R.string.customserver).setShortcut('1', 'c')
        menu.add(0, CANCEL_ID, 3, R.string.cancel).setShortcut('2', 'x')
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return if (item.groupId === SERVER_FOUND_ID) { // Connect to menu item server.
            connect(item.title.toString(), 0)
            true
        } else if (item.groupId === SERVER_FAVORITE_ID) { // Connect to menu item server.
            val slot = item.itemId
            val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val password = preferences.getInt("Password$slot", 0)
            if (!connect(item.title.toString(), password)) {
                val editor = preferences.edit()
                editor.remove("Favorite$slot")
                editor.remove("Password$slot")
                editor.commit()
            }
            true
        } else if (item.itemId === SERVER_CUSTOM_ID) { // Prompt user for server to connect to.
            val alert: AlertDialog.Builder = AlertDialog.Builder(this)
            val to = EditText(this)
            to.inputType = InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
            alert.setTitle(R.string.customserver_title)
            alert.setMessage(R.string.customserver_message)
            alert.setView(to)
            alert.setPositiveButton(R.string.ok_connect,
                DialogInterface.OnClickListener { dialog, whichButton -> connect(to.text.toString()) })
            alert.setNegativeButton(R.string.cancel,
                DialogInterface.OnClickListener { dialog, whichButton -> })
            alert.show()
            true
        } else if (item.itemId === FIND_SERVERS_ID) {
            try {
                val servers = item.subMenu
                findServers(servers)
                if (servers.size() == 0) showErrorDialog(getString(R.string.error_noservers))
            } catch (e: Exception) {
                showErrorDialog(getString(R.string.error), e)
            }
            true
        } else if (item.itemId === FAVORITES_ID) {
            val servers = item.subMenu
            val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            for (i in 0 until MaxServers) {
                val server = preferences.getString("Favorite$i", null)
                val password = preferences.getInt("Password$i", 0)
                if (server != null) servers.add(
                    SERVER_FAVORITE_ID,
                    password,
                    0,
                    server
                ).setShortcut((i + '1'.toInt()).toChar(), (i + 'a'.toInt()).toChar())
            }
            if (servers.size() == 0) showErrorDialog(getString(R.string.error_nofavorites))
            true
        } else {
            super.onContextItemSelected(item)
        }
    }

    // Options menu.
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        //if(!isConnected())
        menu.add(0, CONNECT_ID, 0, R.string.connect).setShortcut('0', 'c')
        //else
        menu.add(0, DISCONNECT_ID, 1, R.string.disconnect).setShortcut('0', 'd')
        //menu.add(0, ADD_FAVORITE_ID, 2, R.string.addfavorite).setShortcut('1', 'f').setEnabled(isConnected());
        menu.add(0, PREFERENCES_ID, 3, R.string.preferences).setShortcut('2', 'p')
        menu.add(0, EXIT_ID, 4, R.string.exit).setShortcut('3', 'q')
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            CONNECT_ID -> {
                registerForContextMenu(touchpad)
                openContextMenu(touchpad)
                unregisterForContextMenu(touchpad)
                return true
            }
            DISCONNECT_ID -> {
                disconnect()
                return true
            }
            ADD_FAVORITE_ID -> {
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val server = preferences.getString("Server", null)
                val password = preferences.getInt("Password", 0)
                if (server != null) {
                    var slot = findFavorite(server)
                    if (slot == -1) slot = findFavorite(null)
                    if (slot != -1) {
                        val editor = preferences.edit()
                        editor.putString("Favorite$slot", server)
                        editor.putInt("Password$slot", password)
                        editor.commit()
                    }
                }
                return true
            }
            PREFERENCES_ID -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            EXIT_ID -> {
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Send packet.
    fun sendPacket(buffer: ByteArray) {
        sendPacket(buffer, true, true)
    }

    fun sendPacket(
        buffer: ByteArray,
        allowConnect: Boolean,
        allowDisconnect: Boolean
    ) {
        try {

            mWebSocket!!.sendBinary(buffer)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to send packet " + buffer[0], e)
            if (allowDisconnect) disconnect()
        }
    }

    // Mouse packets.
    protected fun sendMove(dx: Float, dy: Float) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        writer.put(0x11.toByte())
        writer.put(floatToByte(dx))
        writer.put(floatToByte(dy))
        sendPacket(buffer)
    }

    protected fun sendDown(button: Int) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Down packet.
        writer.put(0x12.toByte())
        writer.put(button.toByte())
        sendPacket(buffer)
    }

    protected fun sendUp(button: Int) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Down packet.
        writer.put(0x13.toByte())
        writer.put(button.toByte())
        sendPacket(buffer)
    }

    protected fun sendClick(button: Int) {
        sendDown(button)
        sendUp(button)
    }

    protected fun sendScroll(d: Float) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        writer.put(0x16.toByte())
        writer.put(floatToByte(d))
        sendPacket(buffer)
    }

    protected fun sendScroll2(dx: Float, dy: Float) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        writer.put(0x17.toByte())
        writer.put(floatToByte(dx))
        writer.put(floatToByte(dy))
        sendPacket(buffer)
    }

    // Keyboard packets.
    fun sendKey(
        control: Byte,
        code: Short,
        flags: Short
    ) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        writer.put(control)
        writer.putShort(code)
        writer.putShort(flags)
        sendPacket(buffer)
    }

    fun sendKeyPress(code: Short, flags: Short) {
        sendKey(0x21.toByte(), code, flags)
    }

    fun sendKeyDown(code: Short, flags: Short) {
        sendKey(0x22.toByte(), code, flags)
    }

    fun sendKeyUp(code: Short, flags: Short) {
        sendKey(0x23.toByte(), code, flags)
    }

    fun sendChar(code: Char) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        writer.put(0x20.toByte())
        writer.putChar(code)
        sendPacket(buffer)
    }

    // Connection packets.
    protected fun sendConnect(password: Int, silent: Boolean ) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        if (silent) writer.put(0x05.toByte()) else writer.put(0x00.toByte())
        writer.putInt(password)
        sendPacket(buffer, false, true)
    }

    protected fun sendDisconnect(silent: Boolean) {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        // Move packet.
        if (silent) writer.put(0x04.toByte()) else writer.put(0x01.toByte())
        sendPacket(buffer, false, false)
    }

    // Keep alive packet.
    protected var nullCount = 0

    protected fun sendNull() {
        val buffer = ByteArray(5)
        val writer: ByteBuffer = ByteBuffer.wrap(buffer)
        writer.order(ByteOrder.BIG_ENDIAN)
        writer.put(0xFF.toByte())
        writer.putInt(nullCount)
        nullCount++
        sendPacket(buffer)
    }

    // Show error dialog
    protected fun showErrorDialog(message: String?) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.error)
        builder.setMessage(message)
        builder.setPositiveButton(R.string.ok, null)
        val dlg: AlertDialog = builder.create()
        dlg.show()
    }

    protected fun showErrorDialog(message: String, e: Throwable) {
        if (e.message != null) showErrorDialog(message + ": " + e.message) else showErrorDialog(
            "$message: $e"
        )
    }

    // Get broadcast address for LAN.
    @Throws(IOException::class)
    protected fun getBroadcastAddress(): InetAddress {
        val wifi = getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifi.dhcpInfo
        // handle null somehow
        val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
        val quads = ByteArray(4)
        for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
        return InetAddress.getByAddress(quads)
    }

    // Clamp float to byte.
    protected fun floatToByte(x: Float): Byte {
        var x = x
        if (x < -128.0f) x = -128.0f
        if (x > 127.0f) x = 127.0f
        return x.toByte()
    }

    // Password hash.
    protected fun Hash(s: String): Int {
        var hash = 5381
        for (i in 0 until s.length) hash = hash * 33 + s[i].toInt()
        return hash
    }
}
