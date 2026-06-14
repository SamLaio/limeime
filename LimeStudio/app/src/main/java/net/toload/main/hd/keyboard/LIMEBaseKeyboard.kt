/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */
/*
 * Derived from Gingerbread inputmethodservice Keyboard.java.
 * Add mKeySizeScale to scale keyboard in vertical direction (height and gap).
 * Jeremy '11,9,4
 */
package net.toload.main.hd.keyboard

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.graphics.Insets
import android.os.Build
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.util.Xml
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import java.io.IOException
import java.util.ArrayList
import java.util.StringTokenizer
import net.toload.main.hd.LIMEKeyboardSwitcher
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import org.xmlpull.v1.XmlPullParserException

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * 
 * The layout file for a keyboard contains XML that looks like the following snippet:
 * <pre>
 * &lt;Keyboard
 * android:keyWidth="%10p"
 * android:keyHeight="50px"
 * android:horizontalGap="2px"
 * android:verticalGap="2px" &gt;
 * &lt;Row android:keyWidth="32px" &gt;
 * &lt;Key android:keyLabel="A" /&gt;
 * ...
 * &lt;/Row&gt;
 * ...
 * &lt;/Keyboard&gt;
</pre> * 
 * 
 * @attr ref R.styleable#Keyboard_keyWidth
 * @attr ref R.styleable#Keyboard_keyHeight
 * @attr ref R.styleable#Keyboard_horizontalGap
 * @attr ref R.styleable#Keyboard_verticalGap
 */
open class LIMEBaseKeyboard(
    context: Context,
    xmlLayoutResId: Int,
    modeId: Int,
    keySizeScale: Float,
    showArrowKeys: Int,
    splitKeyboard: Int
) {
    //private CharSequence mLabel;
    /**
     * Horizontal gap default for all rows
     */
    protected var horizontalGap: Int

    /**
     * Default key width
     */
    protected var keyWidth: Int = 0

    /**
     * Default key height
     */
    protected var keyHeight: Int = 0

    /**
     * Default gap between rows
     */
    protected var verticalGap: Int = 0

    /**
     * Is the keyboard in the shifted state
     */
    open var isShifted: Boolean = false
        protected set

    /**
     * Key instance for the shift key, if present
     */
    private var mShiftKey: Key? = null

    /**
     * Key index for the shift key, if present
     */
    var shiftKeyIndex: Int = -1
        private set

    /* Current key width, while loading the keyboard */ //private int mKeyWidth;
    /* Current key height, while loading the keyboard */ //private int mKeyHeight;
    /**
     * Returns the total height of the keyboard
     * 
     * @return the total height of the keyboard
     */
    /**
     * Total height of the keyboard, including the padding and keys
     */
    var height: Int = 0
        private set

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    var minWidth: Int = 0
        private set

    /**
     * List of keys in this keyboard
     */
    val keys: MutableList<Key>

    /**
     * List of modifier keys such as Shift & Alt, if any
     */
    val modifierKeys: MutableList<Key?>

    /**
     * Width of the screen available to fit the keyboard
     */
    private val mDisplayWidth: Int

    /**
     * Height of the screen
     */
    private val mDisplayHeight: Int

    /**
     * Keyboard mode, or zero, if none.
     */
    private val mKeyboardMode: Int

    /**
     * Show arrow keys on keyboard or not.
     */
    //Add by Jeremy '12,5,21
    @JvmField
    protected var mShowArrowKeys: Int = 0

    /**
     * Key width for separated keyboard in landscape mode.
     */
    @JvmField
    protected var mSplitKeyWidth: Int = this.keyWidth
    var mKeysInRow: Int = DEFAULT_KEYBOARD_COLUMNS

    private var mCellWidth = 0
    private var mCellHeight = 0
    private var mGridNeighbors: Array<IntArray?>? = null
    private var mProximityThreshold = 0


    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the [LIMEBaseKeyboard]
     * defines.
     * 
     * @attr ref R.styleable#Keyboard_keyWidth
     * @attr ref R.styleable#Keyboard_keyHeight
     * @attr ref R.styleable#Keyboard_horizontalGap
     * @attr ref R.styleable#Keyboard_verticalGap
     * @attr ref R.styleable#Keyboard_Row_rowEdgeFlags
     * @attr ref R.styleable#Keyboard_Row_keyboardMode
     */
    class Row {
        /**
         * Default width of a key in this row.
         */
        @JvmField
        var defaultWidth: Int = 0

        /**
         * Default height of a key in this row.
         */
        @JvmField
        var defaultHeight: Int = 0

        /**
         * Default horizontal gap between keys in this row.
         */
        @JvmField
        var defaultHorizontalGap: Int = 0

        /**
         * Vertical gap following this row.
         */
        @JvmField
        var verticalGap: Int = 0

        /**
         * Edge flags for this row of keys. Possible values that can be assigned are
         * [EDGE_TOP][LIMEBaseKeyboard.EDGE_TOP] and [EDGE_BOTTOM][LIMEBaseKeyboard.EDGE_BOTTOM]
         */
        @JvmField
        var rowEdgeFlags: Int = 0

        /**
         * The keyboard mode for this row
         */
        @JvmField
        var mode: Int = 0

        val parent: LIMEBaseKeyboard

        constructor(parent: LIMEBaseKeyboard) {
            this.parent = parent
        }

        constructor(res: Resources, parent: LIMEBaseKeyboard, parser: XmlResourceParser?) {
            this.parent = parent
            res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.LIMEBaseKeyboard
            ).use { a ->
                defaultWidth = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_keyWidth,
                    parent.mDisplayWidth, parent.keyWidth
                )
                defaultHeight = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_keyHeight,  //Jeremy '11,9,4
                    parent.mDisplayHeight, parent.keyHeight, keySizeScale
                )
                defaultHorizontalGap = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_horizontalGap,
                    parent.mDisplayWidth, parent.horizontalGap
                )
                verticalGap = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_verticalGap,  //Jeremy '11,9,4
                    parent.mDisplayHeight, parent.verticalGap, keySizeScale
                )
            }
            res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.LIMEBaseKeyboard_Row
            ).use { a ->
                rowEdgeFlags = a.getInt(R.styleable.LIMEBaseKeyboard_Row_rowEdgeFlags, 0)
                mode = a.getResourceId(R.styleable.LIMEBaseKeyboard_Row_keyboardMode, 0)
            }
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the keyboard.
     * 
     * @attr ref R.styleable#Keyboard_keyWidth
     * @attr ref R.styleable#Keyboard_keyHeight
     * @attr ref R.styleable#Keyboard_horizontalGap
     * @attr ref R.styleable#Keyboard_Key_codes
     * @attr ref R.styleable#Keyboard_Key_keyIcon
     * @attr ref R.styleable#Keyboard_Key_keyLabel
     * @attr ref R.styleable#Keyboard_Key_iconPreview
     * @attr ref R.styleable#Keyboard_Key_isSticky
     * @attr ref R.styleable#Keyboard_Key_isRepeatable
     * @attr ref R.styleable#Keyboard_Key_isModifier
     * @attr ref R.styleable#Keyboard_Key_popupKeyboard
     * @attr ref R.styleable#Keyboard_Key_popupCharacters
     * @attr ref R.styleable#Keyboard_Key_keyOutputText
     * @attr ref R.styleable#Keyboard_Key_keyEdgeFlags
     */
    open class Key {
        /**
         * All the key codes (unicode or custom code) that this key could generate, zero'th
         * being the most important.
         */
        @JvmField
        var codes: IntArray = intArrayOf()

        /**
         * Label to display
         */
        @JvmField
        var label: CharSequence? = null

        /**
         * Icon to display instead of a label. Icon takes precedence over a label
         */
        @JvmField
        var icon: Drawable? = null

        /**
         * Preview version of the icon, for the preview popup
         */
        @JvmField
        var iconPreview: Drawable? = null

        /**
         * Width of the key, not including the gap
         */
        @JvmField
        var width: Int = 0

        /**
         * Height of the key, not including the gap
         */
        @JvmField
        var height: Int = 0

        /**
         * The horizontal gap before this key
         */
        @JvmField
        var gap: Int = 0

        /**
         * Whether this key is sticky, i.e., a toggle key
         */
        @JvmField
        var sticky: Boolean = false

        /**
         * X coordinate of the key in the keyboard layout
         */
        @JvmField
        var x: Int = 0

        /**
         * Y coordinate of the key in the keyboard layout
         */
        @JvmField
        var y: Int = 0

        /**
         * The current pressed state of this key
         */
        @JvmField
        var pressed: Boolean = false

        /**
         * If this is a sticky key, is it on?
         */
        @JvmField
        var on: Boolean = false

        /**
         * Text to output when pressed. This can be multiple characters, like ".com"
         */
        @JvmField
        var text: CharSequence? = null

        /**
         * Popup characters
         */
        @JvmField
        var popupCharacters: CharSequence? = null

        val labelSizeScale: Float
            get() {
                if (DEBUG) Log.i(
                    TAG, ("getLabelSizeScale() "
                            + ", key height = " + height
                            + ", key width = " + width
                            + ", mSplitedKeyWidthScale = " + mSplitedKeyWidthScale
                            + ", keyboard.getKeyHeight = " + keyboard.keyHeight
                            + ", keyboard.getKeyWidth() = " + keyboard.keyWidth)
                )
                if (mLabelSizeScale > 0) return mLabelSizeScale

                //Jeremy '12,6, 7 move from LIMEkeyboardbaseview
                mLabelSizeScale = 1f


                if (width < keyboard.keyWidth)  //Jeremy '12,5,26 scaled the label size if the key width is smaller than default key width
                    mLabelSizeScale =
                        if (mSplitKeyboard) 1f else mSplitedKeyWidthScale

                //*=  (float)(width) / (float)(keyboard.getKeyWidth());
                return mLabelSizeScale
            }

        /**
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events
         * that are just out of the boundary of the key. This is a bit mask of
         * [LIMEBaseKeyboard.EDGE_LEFT], [LIMEBaseKeyboard.EDGE_RIGHT], [LIMEBaseKeyboard.EDGE_TOP] and
         * [LIMEBaseKeyboard.EDGE_BOTTOM].
         */
        @JvmField
        var edgeFlags: Int = 0

        /**
         * Whether this is a modifier key, such as Shift or Alt
         */
        @JvmField
        var modifier: Boolean = false

        var isFunctionalKey: Boolean
            get() = modifier
            set(value) {
                modifier = value
            }

        /**
         * The keyboard that this key belongs to
         */
        private val keyboard: LIMEBaseKeyboard

        /**
         * If this key pops up a mini keyboard, this is the resource id for the XML layout for that
         * keyboard.
         */
        @JvmField
        var popupResId: Int = 0

        /**
         * Whether this key repeats itself when held down
         */
        @JvmField
        var repeatable: Boolean = false


        // moved from LIMEKeybard by Jeremy '12,5,22
        private val KEY_STATE_FUNCTIONAL_NORMAL = intArrayOf(
            android.R.attr.state_single
        )

        // functional pressed state (with properties)
        private val KEY_STATE_FUNCTIONAL_PRESSED = intArrayOf(
            android.R.attr.state_single,
            android.R.attr.state_pressed
        )


        /**
         * Create an empty key with no attributes.
         */
        constructor(parent: Row) {
            keyboard = parent.parent
        }

        /**
         * Clone a key with same attributes
         */
        constructor(parent: Row, key: Key) {
            keyboard = parent.parent

            x = key.x
            y = key.y

            width = key.width
            height = key.height
            gap = key.gap
            codes = key.codes
            iconPreview = key.iconPreview
            popupCharacters = key.popupCharacters
            popupResId = key.popupResId
            repeatable = key.repeatable
            this.isFunctionalKey = key.isFunctionalKey
            sticky = key.sticky
            edgeFlags = key.edgeFlags
            icon = key.icon
            label = key.label
            text = key.text
            if (iconPreview != null) {
                iconPreview!!.setBounds(
                    0, 0, iconPreview!!.getIntrinsicWidth(),
                    iconPreview!!.getIntrinsicHeight()
                )
            }
            if (icon != null) {
                icon!!.setBounds(0, 0, icon!!.getIntrinsicWidth(), icon!!.getIntrinsicHeight())
            }
            if (codes.isEmpty() && !TextUtils.isEmpty(label)) {
                codes = intArrayOf(label!!.get(0).code)
            }
        }

        /**
         * Create a key with the given top-left coordinate and extract its attributes from
         * the XML parser.
         * 
         * @param res    resources associated with the caller's context
         * @param parent the row that this key belongs to. The row must already be attached to
         * a [LIMEBaseKeyboard].
         * @param x      the x coordinate of the top-left
         * @param y      the y coordinate of the top-left
         * @param parser the XML parser containing the attributes for this key
         */
        constructor(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser?) : this(
            parent
        ) {
            this.x = x
            this.y = y

            var keyWidthScale = 1f
            if (mSplitKeyboard) keyWidthScale = mSplitedKeyWidthScale
            if (DEBUG) Log.i(
                TAG,
                "Key(): key.mSeperatedKeyboard = " + mSplitKeyboard + ". keyWidthScale = " + keyWidthScale
            )

            res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.LIMEBaseKeyboard
            ).use { a ->
                width = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_keyWidth,
                    keyboard.mDisplayWidth, Math.round(parent.defaultWidth * keyWidthScale),
                    keyWidthScale
                ) //Jeremy '12,5,26

                height = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_keyHeight,
                    keyboard.mDisplayHeight, parent.defaultHeight, keySizeScale
                ) //Jeremy '11,9,3
                gap = getDimensionOrFraction(
                    a,
                    R.styleable.LIMEBaseKeyboard_horizontalGap,
                    keyboard.mDisplayWidth, parent.defaultHorizontalGap
                )
            }
            this.x += gap

            res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.LIMEBaseKeyboard_Key
            ).use { a ->
                val codesValue = TypedValue()
                a.getValue(
                    R.styleable.LIMEBaseKeyboard_Key_codes,
                    codesValue
                )
                if (codesValue.type == TypedValue.TYPE_INT_DEC
                    || codesValue.type == TypedValue.TYPE_INT_HEX
                ) {
                    codes = intArrayOf(codesValue.data)
                } else if (codesValue.type == TypedValue.TYPE_STRING) {
                    codes = parseCSV(codesValue.string.toString())
                }

                iconPreview = a.getDrawable(R.styleable.LIMEBaseKeyboard_Key_iconPreview)
                if (iconPreview != null) {
                    iconPreview!!.setBounds(
                        0, 0, iconPreview!!.getIntrinsicWidth(),
                        iconPreview!!.getIntrinsicHeight()
                    )
                }
                popupCharacters = a.getText(
                    R.styleable.LIMEBaseKeyboard_Key_popupCharacters
                )
                popupResId = a.getResourceId(
                    R.styleable.LIMEBaseKeyboard_Key_popupKeyboard, 0
                )
                repeatable = a.getBoolean(
                    R.styleable.LIMEBaseKeyboard_Key_isRepeatable, false
                )
                this.isFunctionalKey = a.getBoolean(
                    R.styleable.LIMEBaseKeyboard_Key_isModifier, false
                )
                sticky = a.getBoolean(
                    R.styleable.LIMEBaseKeyboard_Key_isSticky, false
                )
                edgeFlags = a.getInt(R.styleable.LIMEBaseKeyboard_Key_keyEdgeFlags, 0)
                edgeFlags = edgeFlags or parent.rowEdgeFlags

                icon = a.getDrawable(
                    R.styleable.LIMEBaseKeyboard_Key_keyIcon
                )
                if (icon != null) {
                    icon!!.setBounds(0, 0, icon!!.getIntrinsicWidth(), icon!!.getIntrinsicHeight())
                }
                label = a.getText(R.styleable.LIMEBaseKeyboard_Key_keyLabel)
                text = a.getText(R.styleable.LIMEBaseKeyboard_Key_keyOutputText)
                if (codes.isEmpty() && !TextUtils.isEmpty(label)) {
                    codes = intArrayOf(label!!.get(0).code)
                }
            }
        }

        /**
         * Informs the key that it has been pressed, in case it needs to change its appearance or
         * state.
         * 
         * @see .onReleased
         */
        fun onPressed() {
            pressed = !pressed
        }

        /**
         * Changes the pressed state of the key. If it is a sticky key, it will also change the
         * toggled state of the key if the finger was release inside.
         * 
         * @param inside whether the finger was released inside the key
         * @see .onPressed
         */
        open fun onReleased(inside: Boolean) {
            pressed = !pressed
            if (sticky) {
                on = !on
            }
        }

        fun parseCSV(value: String): IntArray {
            var count = 0
            var lastIndex = 0
            if (!value.isEmpty()) {
                do {
                    count++
                } while ((value.indexOf(",", lastIndex + 1).also { lastIndex = it }) > 0)
            }
            val values = IntArray(count)
            count = 0
            val st = StringTokenizer(value, ",")
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = st.nextToken().toInt()
                } catch (nfe: NumberFormatException) {
                    Log.e(TAG, "Error parsing keycodes " + value)
                }
            }
            return values
        }

        /**
         * Detects if a point falls inside this key.
         * 
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an edge,
         * it will assume that all points between the key and the edge are considered to be inside
         * the key.
         */
        open fun isInside(x: Int, y: Int): Boolean {
            val leftEdge = (edgeFlags and EDGE_LEFT) > 0
            val rightEdge = (edgeFlags and EDGE_RIGHT) > 0
            val topEdge = (edgeFlags and EDGE_TOP) > 0
            val bottomEdge = (edgeFlags and EDGE_BOTTOM) > 0
            return (x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))
        }

        /**
         * Returns the square of the distance between the center of the key and the given point.
         * 
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return the square of the distance of the point from the center of the key
         */
        fun squaredDistanceFrom(x: Int, y: Int): Int {
            val xDist = this.x + width / 2 - x
            val yDist = this.y + height / 2 - y
            return xDist * xDist + yDist * yDist
        }

        val currentDrawableState: IntArray
            /**
             * Returns the drawable state for the key, based on the current state and type of the key.
             * 
             * @return the drawable state of the key.
             * @see android.graphics.drawable.StateListDrawable.setState
             */
            get() {
                val states: IntArray
                if (sticky) {
                    if (on) {
                        if (pressed) {
                            states =
                                KEY_STATE_PRESSED_ON
                        } else {
                            states =
                                KEY_STATE_NORMAL_ON
                        }
                    } else {
                        if (pressed) {
                            states =
                                KEY_STATE_PRESSED_OFF
                        } else {
                            states =
                                KEY_STATE_NORMAL_OFF
                        }
                    }
                } else if (this.isFunctionalKey) {
                    if (pressed) {
                        states = KEY_STATE_FUNCTIONAL_PRESSED
                    } else {
                        states = KEY_STATE_FUNCTIONAL_NORMAL
                    }
                } else {
                    if (pressed) {
                        states =
                            KEY_STATE_PRESSED
                    } else {
                        states =
                            KEY_STATE_NORMAL
                    }
                }
                return states
            }

        companion object {
            /**
             * LabelSizeScale Jeremy '12,6,7
             */
            private var mLabelSizeScale = 0f

            private val KEY_STATE_NORMAL_ON = intArrayOf(
                android.R.attr.state_single,
                android.R.attr.state_checkable,
                android.R.attr.state_checked
            )

            private val KEY_STATE_PRESSED_ON = intArrayOf(
                android.R.attr.state_single,
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked
            )

            private val KEY_STATE_NORMAL_OFF = intArrayOf(
                android.R.attr.state_single,
                android.R.attr.state_checkable
            )

            private val KEY_STATE_PRESSED_OFF = intArrayOf(
                android.R.attr.state_single,
                android.R.attr.state_pressed,
                android.R.attr.state_checkable
            )

            private val KEY_STATE_NORMAL = intArrayOf()

            private val KEY_STATE_PRESSED = intArrayOf(
                android.R.attr.state_pressed
            )
        }
    }

    /**
     * Creates a keyboard from the given xml key layout file.
     * 
     * @param context        the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     */
    constructor(
        context: Context,
        xmlLayoutResId: Int,
        keySizeScale: Float,
        showArrowKeys: Int,
        splitKeyboard: Int
    ) : this(context, xmlLayoutResId, 0, keySizeScale, showArrowKeys, splitKeyboard)

    /**
     * 
     * Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     * 
     * 
     * If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.
     * 
     * @param context             the application or service context
     * @param layoutTemplateResId the layout template file, containing no keys.
     * @param characters          the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns             the number of columns of keys to display. If this number is greater than the
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the
     * keyboard will fit as many keys as possible in each row.
     */
    constructor(
        context: Context, layoutTemplateResId: Int,
        characters: CharSequence, columns: Int, horizontalPadding: Int, keySizeScale: Float
    ) : this(
        context,
        layoutTemplateResId,
        keySizeScale,
        0,
        0
    ) //Jeremy '12,5,21 never show arrow keys in popup keyboard
    {
        var characters = characters
        var x = 0
        var y = 0
        var column = 0
        this.minWidth = 0


        val row = Row(this)
        row.defaultHeight = (this.keyHeight * Companion.keySizeScale).toInt()
        row.defaultWidth = this.keyWidth
        row.defaultHorizontalGap = this.horizontalGap
        row.verticalGap = (this.verticalGap * Companion.keySizeScale).toInt()
        row.rowEdgeFlags = EDGE_TOP or EDGE_BOTTOM
        val maxColumns = if (columns == -1) Int.Companion.MAX_VALUE else columns

        var labels: CharSequence? = null
        if (characters.toString().contains("\n")) {
            val charactersAndLabel: Array<String?> =
                characters.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            characters = charactersAndLabel[0]!!
            labels = charactersAndLabel[1]
        }

        for (i in 0..<characters.length) {
            val c = characters.get(i)
            if (column >= maxColumns
                || x + this.keyWidth + horizontalPadding > mDisplayWidth
            ) {
                x = 0
                y += this.verticalGap + this.keyHeight
                column = 0
            }
            val key = Key(row)
            key.x = x
            key.y = y
            key.width = this.keyWidth
            key.height = (this.keyHeight * Companion.keySizeScale).toInt()
            key.gap = this.horizontalGap
            if (labels == null)  //Jeremy '12,5,21 add keylabels in popupcharacters seperated as \n. The format is "123\nABC" ABC are keylabels for 123.
                key.label = c.toString()
            else key.label = c.toString() + "\n" + labels.get(i)
            key.codes = intArrayOf(c.code)
            column++
            x += key.width + key.gap
            keys.add(key)
            if (x > this.minWidth) {
                this.minWidth = x
            }
        }
        this.height = y + row.defaultHeight //mDefaultHeight;
    }

    open fun setShifted(shiftState: Boolean): Boolean {
        if (mShiftKey != null) {
            mShiftKey!!.on = shiftState
        }
        if (this.isShifted != shiftState) {
            this.isShifted = shiftState
            return true
        }
        return false
    }

    private fun computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (this.minWidth + GRID_WIDTH - 1) / GRID_WIDTH
        mCellHeight = (this.height + GRID_HEIGHT - 1) / GRID_HEIGHT
        mGridNeighbors = arrayOfNulls<IntArray>(GRID_SIZE)
        val indices = IntArray(keys.size)
        val gridWidth: Int = GRID_WIDTH * mCellWidth
        val gridHeight: Int = GRID_HEIGHT * mCellHeight
        var x = 0
        while (x < gridWidth) {
            var y = 0
            while (y < gridHeight) {
                var count = 0
                for (i in keys.indices) {
                    val key = keys.get(i)
                    if (key.squaredDistanceFrom(
                            x,
                            y
                        ) < mProximityThreshold || key.squaredDistanceFrom(
                            x + mCellWidth - 1,
                            y
                        ) < mProximityThreshold || (key.squaredDistanceFrom(
                            x + mCellWidth - 1,
                            y + mCellHeight - 1
                        )
                                < mProximityThreshold) || key.squaredDistanceFrom(
                            x,
                            y + mCellHeight - 1
                        ) < mProximityThreshold
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                mGridNeighbors!![(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell
                y += mCellHeight
            }
            x += mCellWidth
        }
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     * 
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(x: Int, y: Int): IntArray? {
        if (mGridNeighbors == null) computeNearestNeighbors()
        if (x >= 0 && x < this.minWidth && y >= 0 && y < this.height) {
            val index: Int = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)
            if (index < GRID_SIZE) {
                return mGridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    protected fun createRowFromXml(res: Resources, parser: XmlResourceParser?): Row {
        return Row(res, this, parser)
    }

    protected open fun createKeyFromXml(
        context: Context, parent: Row, x: Int, y: Int,
        parser: XmlResourceParser?
    ): Key {
        return Key(context.getResources(), parent, x, y, parser)
    }

    /**
     * createArrowKeysRow() returns the total height of the row.
     */
    val ARROW_KEY_HEIGHT_FRACTION: Float = 0.8f

    /**
     * Creates a keyboard from the given xml key layout file. Weeds out rows
     * that have a keyboard mode defined but don't match the specified mode.
     * 
     * @param context        the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param modeId         keyboard mode identifier
     */
    init {
        val dm = context.getResources().getDisplayMetrics()
        // Issue #47: use the actual usable window width (excluding system bars and
        // display cutout insets) so percentage-based key widths don't overflow the
        // IME container on devices with notches, gesture nav, or split-screen.
        mDisplayWidth = getUsableDisplayWidth(context, dm.widthPixels)
        mDisplayHeight = dm.heightPixels

        if (DEBUG) Log.i(
            TAG,
            "LIMEBaseKeyboard() mDisplayWidth = " + mDisplayWidth + ". mDisplayHeight" + mDisplayHeight
        )


        this.horizontalGap = 0
        this.keyWidth = mDisplayWidth / DEFAULT_KEYBOARD_COLUMNS
        this.verticalGap = 0
        this.keyHeight = this.keyWidth
        this.keys = ArrayList<Key>()
        this.modifierKeys = ArrayList<Key?>()
        mKeyboardMode = modeId
        Companion.keySizeScale = keySizeScale
        mShowArrowKeys = showArrowKeys

        val mLandScape = mDisplayWidth > mDisplayHeight

        context.getTheme().obtainStyledAttributes(
            null,
            R.styleable.LIMEBaseKeyboard,
            R.attr.LIMEBaseKeyboardStyle,
            R.style.LIMEBaseKeyboard
        ).use { a ->
            mDrawableArrowUp = a.getDrawable(R.styleable.LIMEBaseKeyboard_drawableArrowUp)
            mDrawableArrowDown = a.getDrawable(R.styleable.LIMEBaseKeyboard_drawableArrowDown)
            mDrawableArrowLeft = a.getDrawable(R.styleable.LIMEBaseKeyboard_drawableArrowLeft)
            mDrawableArrowRight = a.getDrawable((R.styleable.LIMEBaseKeyboard_drawableArrowRight))
        }
        //Jeremy '12,5,26 reserve  columns in the middle for arrow keys in landscape mode.
        //Jeremy '12,5,27 read splitkeyboard setting from preference. 
        //Jeremy '12,6,19  add orientation consideration on split keyboard
        mSplitKeyboard = (mLandScape && mShowArrowKeys != 0)
                || (mLandScape && splitKeyboard == SPLIT_KEYBOARD_LANDSCAPD_ONLY)
                || splitKeyboard == SPLIT_KEYBOARD_ALWAYS

        loadKeyboard(context, context.getResources().getXml(xmlLayoutResId))
    }

    protected fun createArrowKeys(res: Resources?, x: Int, y: Int, verticalLayout: Boolean): Int {
        var x = x
        var y = y
        if (DEBUG) Log.i(TAG, "createArrowKeys(): mDisplayWidth = " + mDisplayWidth)

        val row = Row(this)

        row.verticalGap = (this.verticalGap * keySizeScale).toInt()
        row.defaultHorizontalGap = this.horizontalGap
        if (verticalLayout) {
            row.defaultHeight = (this.height - 3 * row.verticalGap) / 4
            row.defaultWidth = mSplitKeyWidth
        } else {
            row.defaultHeight = (this.keyHeight * keySizeScale * ARROW_KEY_HEIGHT_FRACTION).toInt()
            row.defaultWidth = Math.round((mDisplayWidth - 3 * this.horizontalGap).toFloat() / 4)
            row.rowEdgeFlags = EDGE_TOP or EDGE_BOTTOM
        }


        // Many special symbols : http://star.gg/special-symbols
        for (i in 0..3) {
            val key = Key(row)
            key.x = x
            key.y = y
            key.width = row.defaultWidth
            key.height = row.defaultHeight
            key.gap = row.defaultHorizontalGap
            key.isFunctionalKey = true


            // Cross shape arrow keys layout if center reserved space is larger than 2 , Jeremy '12,5,28
            if (verticalLayout && mReservedColumnsForSplitedKeyboard > 2) {
                when (i) {
                    0 -> {
                        key.icon = mDrawableArrowUp
                        key.codes = intArrayOf(KEYCODE_UP)
                        y += key.height + row.verticalGap
                        x -= key.width / 2 + key.gap
                    }

                    1 -> {
                        key.icon = mDrawableArrowLeft
                        key.codes = intArrayOf(KEYCODE_LEFT)
                        x += key.width + key.gap * 2
                    }

                    2 -> {
                        key.icon = mDrawableArrowRight
                        key.codes = intArrayOf(KEYCODE_RIGHT)
                        x -= key.width / 2 + key.gap
                        y += key.height + row.verticalGap
                    }

                    3 -> {
                        key.icon = mDrawableArrowDown
                        key.codes = intArrayOf(KEYCODE_DOWN)
                        y += key.height + row.verticalGap
                    }
                }
            } else {
                when (i) {
                    0 -> {
                        key.icon = mDrawableArrowUp
                        key.codes = intArrayOf(KEYCODE_UP)
                    }

                    1 -> {
                        key.icon = mDrawableArrowDown
                        key.codes = intArrayOf(KEYCODE_DOWN)
                    }

                    2 -> {
                        key.icon = mDrawableArrowLeft
                        key.codes = intArrayOf(KEYCODE_LEFT)
                    }

                    3 -> {
                        key.icon = mDrawableArrowRight
                        key.codes = intArrayOf(KEYCODE_RIGHT)
                    }
                }
                if (verticalLayout) y += key.height + row.verticalGap
                else x += key.width + key.gap
            }


            if (DEBUG) Log.i(TAG, "createArrowKeysRow(): key[" + i + "]" + "; x = " + x)

            keys.add(key)
            //mModifierKeys.add(key);
            if (x > this.minWidth) {
                this.minWidth = x
            }
        }


        return (row.defaultHeight + row.verticalGap) //return the row total height
    }


    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        var inKey = false
        var inRow = false

        //boolean leftMostKey = false;

        //int row = 0;
        var x = 0
        var y = 0
        var key: Key? = null
        var currentRow: Row? = null
        val res = context.getResources()
        var skipRow: Boolean

        /* Show arrow keys on top of the soft keyboard in portrait mode.*/
        val showArrowKeysOnTop = (mShowArrowKeys == 1) && (mDisplayWidth < mDisplayHeight)
        /* Show arrow keys on bottom of the soft keyboard in portrait mode.*/
        val showArrowKeysOnBottom = (mShowArrowKeys == 2) && (mDisplayWidth < mDisplayHeight)
        /* The left bound of the center blank area on split keyboard. */
        var leftSplitBorder = 0
        /* The distance to be shifted for right side keyboard */
        var splitDistance = 0
        /* The centerLine of current screen in horizontal direction. */
        val centerLine = mDisplayWidth / 2
        /* Reserved center space for arrow keys on right or left of the center line. */
        var reservedCenterSpace = 0

        try {
            var event: Int


            while ((parser.next().also { event = it }) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    val tag = parser.getName()
                    if (TAG_ROW == tag) {
                        inRow = true
                        x = 0

                        currentRow = createRowFromXml(res, parser)
                        skipRow = !matchesKeyboardMode(currentRow.mode)
                        if (skipRow) {
                            skipToEndOfRow(parser)
                            inRow = false
                        }
                    } else if (TAG_KEY == tag) {
                        inKey = true
                        key = createKeyFromXml(context, currentRow!!, x, y, parser)
                        keys.add(key)

                        //Jeremy '12,5,26 shift the keys after separated threshold and 
                        // repeat space keys or keys longer then the distance between space key and half of screen  on right keyboard.
                        if (DEBUG) Log.i(
                            TAG, ("loadkeyboard():"
                                    + ". key.x = " + key.x
                                    + ". key.width = " + key.width)
                        )


                        if (mSplitKeyboard && leftSplitBorder > 0 && ((key.x >= leftSplitBorder && key.x < centerLine) //key left bound in between split border and centerline
                                    || (key.x < leftSplitBorder //the key right bound is too closed to centerline so as no enough clearance for center arrow keys
                                    && key.x + key.width >= centerLine - reservedCenterSpace && key.x + splitDistance > centerLine + reservedCenterSpace
                                    ))
                        ) {
                            key.x += splitDistance
                            x += splitDistance
                            if (DEBUG) Log.i(
                                TAG, ("loadkeyboard(): shitfing"
                                        + ". key.x = " + key.x
                                        + ". key.width = " + key.width
                                        + ". x = " + x
                                        + ". y = " + y)
                            )
                        } else if (mSplitKeyboard && leftSplitBorder > 0 && ((key.codes!![0] == KEYCODE_SPACE && key.x < leftSplitBorder)
                                    || (key.x < leftSplitBorder && key.x + key.width > centerLine))

                        ) {
                            val keyRightBound = key.x + key.width + splitDistance

                            if (DEBUG) Log.i(
                                TAG, ("loadkeyboard() split keys,  keyRightBound = " + keyRightBound
                                        + ". key.x = " + key.x
                                        + ". key.width = " + key.width
                                        + ". x = " + x
                                        + ". y = " + y)
                            )

                            // add space key in right side split keyboard Jeremy '12,5,26
                            if (keyRightBound > centerLine + key.gap + mSplitKeyWidth + reservedCenterSpace) {
                                key.width = centerLine - key.x - key.gap - reservedCenterSpace
                                checkNotNull(currentRow)
                                val rightKey = Key(
                                    currentRow,
                                    key
                                ) //clone the space key for the space key on right keyboard.
                                rightKey.x = centerLine + key.gap + reservedCenterSpace
                                rightKey.width = keyRightBound - rightKey.x
                                keys.add(rightKey)
                                x += rightKey.gap * 2 + rightKey.width + reservedCenterSpace * 2 //shift x for the distance on center reserved space + right key width
                            }
                        }


                        if (key.codes!![0] == KEYCODE_SHIFT) {
                            mShiftKey = key
                            this.shiftKeyIndex = keys.size - 1
                            modifierKeys.add(key)
                        } else if (key.codes!![0] == KEYCODE_ALT) {
                            modifierKeys.add(key)
                        }
                    } else if (TAG_KEYBOARD == tag) {
                        parseKeyboardAttributes(res, parser)

                        if (mSplitKeyboard) {
                            leftSplitBorder =
                                (mKeysInRow / 2 - 1) * this.horizontalGap + (mKeysInRow / 2) * mSplitKeyWidth
                            splitDistance = mDisplayWidth - mKeysInRow * mSplitKeyWidth
                            if (mReservedColumnsForSplitedKeyboard > 2) reservedCenterSpace =
                                mSplitKeyWidth //reserved 2 columns in the center
                            else reservedCenterSpace =
                                mSplitKeyWidth / 2 //reserved 1 columns in the center

                            if (DEBUG) Log.i(
                                TAG,
                                ("loadkeyboard() keyboard attributed parsed, leftSplitBorder = " + leftSplitBorder
                                        + ". keysInRow = " + mKeysInRow
                                        + ". mSeparatedKeyWidth = " + mSplitKeyWidth
                                        + ". splitDistance = " + splitDistance
                                        + ". centerLine = " + centerLine)
                            )
                        }


                        if (showArrowKeysOnTop)  //Jeremy '12,5,24 create arrow keys before reading further rows.
                            y += createArrowKeys(res, 0, 0, false)
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false
                        x += key!!.gap + key.width

                        if (DEBUG) Log.i(
                            TAG, ("loadKeyboard() inkey: x = " + x
                                    + ". kye.gap = " + key.gap
                                    + ". key.width = " + key.width
                                    + ". splitDistance = " + splitDistance)
                        )


                        if (x > this.minWidth) {
                            this.minWidth = x
                        }
                    } else if (inRow) {
                        inRow = false
                        y += currentRow!!.verticalGap
                        y += currentRow.defaultHeight
                        //row++;
                    }
                    //else {// TODO: error or extend?}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error:" + e)
            Log.e(TAG_KEYBOARD, "Error in keyboard operation", e)
        }
        /* Add arrow keys row if mShowArrowKeys is on */  //Add by Jeremy '12,5,21
        if (showArrowKeysOnBottom) y += createArrowKeys(res, 0, y, false)

        this.height = y - this.verticalGap

        if (mSplitKeyboard && mShowArrowKeys != 0 && mDisplayWidth > mDisplayHeight) createArrowKeys(
            res,
            (mDisplayWidth - mSplitKeyWidth) / 2,
            0,
            true
        )


        if (DEBUG) Log.i(TAG, "loadKeyboard():mTotalHeight" + this.height)
    }

    private fun matchesKeyboardMode(rowMode: Int): Boolean {
        if (rowMode == 0 || rowMode == mKeyboardMode) return true
        return when (rowMode) {
            R.id.mode_normal -> mKeyboardMode == LIMEKeyboardSwitcher.MODE_TEXT
            R.id.mode_url -> mKeyboardMode == LIMEKeyboardSwitcher.MODE_URL
            R.id.mode_email -> mKeyboardMode == LIMEKeyboardSwitcher.MODE_EMAIL
            R.id.mode_im -> mKeyboardMode == LIMEKeyboardSwitcher.MODE_IM
            else -> false
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skipToEndOfRow(parser: XmlResourceParser) {
        var event: Int
        while ((parser.next().also { event = it }) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG
                && parser.getName() == TAG_ROW
            ) {
                break
            }
        }
    }

    private fun parseKeyboardAttributes(res: Resources, parser: XmlResourceParser?) {
        res.obtainAttributes(
            Xml.asAttributeSet(parser),
            R.styleable.LIMEBaseKeyboard
        ).use { a ->
            this.keyWidth = getDimensionOrFraction(
                a,
                R.styleable.LIMEBaseKeyboard_keyWidth,
                mDisplayWidth, mDisplayWidth / DEFAULT_KEYBOARD_COLUMNS
            )
            this.keyHeight = getDimensionOrFraction(
                a,
                R.styleable.LIMEBaseKeyboard_keyHeight,  //Jeremy '11,9,4
                mDisplayHeight, DEFAULT_KEY_HEIGHT_PX, keySizeScale
            )
            this.horizontalGap = getDimensionOrFraction(
                a,
                R.styleable.LIMEBaseKeyboard_horizontalGap,
                mDisplayWidth, 0
            )
            this.verticalGap = getDimensionOrFraction(
                a,
                R.styleable.LIMEBaseKeyboard_verticalGap,  //Jeremy '11,9,4
                mDisplayHeight, 0, keySizeScale
            )
        }
        /*
         * Number of key widths from current touch point to search for nearest keys.
         */
        val SEARCH_DISTANCE = 1.8f
        mProximityThreshold = (this.keyWidth * SEARCH_DISTANCE).toInt()
        mProximityThreshold = mProximityThreshold * mProximityThreshold // Square it for comparison

        //Jeremy '12,5,26 for seperated keyboard in landscape with arrow keys
        mReservedColumnsForSplitedKeyboard =
            res.getInteger(R.integer.reserved_columns_for_seperated_keyboard)

        mKeysInRow = Math.round(mDisplayWidth.toFloat() / this.keyWidth)
        mSplitKeyWidth =
            Math.round(mDisplayWidth.toFloat() / (mKeysInRow + mReservedColumnsForSplitedKeyboard))
        mSplitedKeyWidthScale = (mSplitKeyWidth).toFloat() / (this.keyWidth).toFloat()
        if (DEBUG) Log.i(
            TAG, ("mKeysInRow = " + mKeysInRow
                    + ". mDisplayWidth = " + mDisplayWidth
                    + ". mDefaultWidth = " + this.keyWidth
                    + ". mSeparatedKeyWidth = " + mSplitKeyWidth
                    + ". mSeperatedKeyWidthScale = " + mSplitedKeyWidthScale)
        )
    }

    fun getKeySizeScale(): Float {
        return keySizeScale
    }

    fun setKeySizeScale(keySizeScale: Float) {
        Companion.keySizeScale = keySizeScale
    }

    companion object {
        const val TAG: String = "LIMEBaseKeyboard"
        private const val DEBUG = false

        // Keyboard XML Tags
        private const val TAG_KEYBOARD = "Keyboard"
        private const val TAG_ROW = "Row"
        private const val TAG_KEY = "Key"

        const val EDGE_LEFT: Int = 0x01
        const val EDGE_RIGHT: Int = 0x02
        const val EDGE_TOP: Int = 0x04
        const val EDGE_BOTTOM: Int = 0x08

        @JvmField
        val KEYCODE_SHIFT: Int = -1
        @JvmField
        val KEYCODE_MODE_CHANGE: Int = -2
        @JvmField
        val KEYCODE_DONE: Int = -3

        @JvmField
        val KEYCODE_DELETE: Int = -5
        @JvmField
        val KEYCODE_ALT: Int = -6
        @JvmField
        val KEYCODE_UP: Int = -11
        @JvmField
        val KEYCODE_DOWN: Int = -12
        @JvmField
        val KEYCODE_LEFT: Int = -13
        @JvmField
        val KEYCODE_RIGHT: Int = -14

        //Jeremy '12,5,26 moved from LIMEKeyboard
        @JvmField
        val KEYCODE_ENTER: Int = '\n'.code
        @JvmField
        val KEYCODE_SPACE: Int = ' '.code

        //Jeremy '12,6,19
        const val SPLIT_KEYBOARD_NEVER: Int = 0
        const val SPLIT_KEYBOARD_ALWAYS: Int = 1
        const val SPLIT_KEYBOARD_LANDSCAPD_ONLY: Int = 2

        /**
         * Drawable for arrow keys
         */
        private var mDrawableArrowUp: Drawable? = null
        private var mDrawableArrowDown: Drawable? = null
        private var mDrawableArrowRight: Drawable? = null
        private var mDrawableArrowLeft: Drawable? = null


        /**
         * KeySizeScale Jeremy '11,9,3
         */
        var keySizeScale: Float = 1f


        /**
         * Show separated keyboard with arrow keys in the middle.
         */
        //Add by Jeremy '12,5,26
        @JvmField
        protected var mSplitKeyboard: Boolean = false

        /**
         * Reserved space in the middle in unit of columns for separated keyboard in landscape mode.
         */
        @JvmField
        protected var mReservedColumnsForSplitedKeyboard: Int = 2

        /**
         * Key width reduction scale for separated keyboard in landscape mode.
         */
        @JvmField
        protected var mSplitedKeyWidthScale: Float = 1f

        /**
         * Default key number in a row .
         */
        // UI Dimension Constants (in pixels/dp)
        private const val DEFAULT_KEYBOARD_COLUMNS = 10 // Default number of keys per row
        private const val DEFAULT_KEY_HEIGHT_PX = 50 // Default keyboard key height
        private const val KEYBOARD_GRID_WIDTH = 10 // Grid width for proximity calculation
        private const val KEYBOARD_GRID_HEIGHT = 5 // Grid height for proximity calculation

        // Variables for pre-computing nearest keys.
        private val GRID_WIDTH: Int = KEYBOARD_GRID_WIDTH
        private val GRID_HEIGHT: Int = KEYBOARD_GRID_HEIGHT
        private val GRID_SIZE: Int = GRID_WIDTH * GRID_HEIGHT

        /**
         * Issue #47: returns the actual usable width for the IME window, excluding system
         * bar and display cutout insets. Falls back to the supplied display-metrics width
         * on Android versions older than R (API 30) where [WindowMetrics] is not
         * available.
         */
        private fun getUsableDisplayWidth(context: Context, fallbackWidthPx: Int): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
                    if (wm != null) {
                        val metrics = wm.getCurrentWindowMetrics()
                        val insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                        )
                        val width = metrics.getBounds().width() - insets.left - insets.right
                        if (width > 0) return width
                    }
                } catch (t: Throwable) {
                    Log.w(
                        TAG,
                        "getUsableDisplayWidth() failed, falling back to displayMetrics: " + t
                    )
                }
            }
            return fallbackWidthPx
        }

        fun getDimensionOrFraction(a: TypedArray, index: Int, base: Int, defValue: Int): Int {
            return getDimensionOrFraction(a, index, base, defValue, 1f)
        }

        fun getDimensionOrFraction(
            a: TypedArray,
            index: Int,
            base: Int,
            defValue: Int,
            scale: Float
        ): Int {
            val value = a.peekValue(index)
            if (value == null) return defValue
            if (value.type == TypedValue.TYPE_DIMENSION) {
                //Log.i(TAG, "getDimensionOrFraction() got dimension value, defvalue = " + defValue + ". scale = " +scale);
                return (a.getDimensionPixelOffset(index, defValue) * scale).toInt() //Jeremy '11,9,4
            } else if (value.type == TypedValue.TYPE_FRACTION) {
                // Round it to avoid values like 47.9999 from getting truncated
                //Log.i(TAG, "getDimensionOrFraction() got fraction value, base = " + base + ". defvalue = " + defValue + ". scale = " +scale);
                return (a.getFraction(
                    index,
                    base,
                    base,
                    defValue.toFloat()
                ) * scale).toInt() //Jeremy '12,5,26 add scale. '12,5,27 use (int) instead of round to avoid rouding error
            }
            return defValue
        }
    }
}
