package com.samsung.sprc.fileselector;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

public class EditTextCursorWatcher extends AppCompatEditText {
    public EditTextCursorWatcher(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EditTextCursorWatcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextCursorWatcher(Context context) {
        super(context);
    }

    private OnSelChangedListener listener = null;

    public interface OnSelChangedListener {
        void onSelChanged(int selStart, int selEnd);
    }

    public void setOnSelectionChangedListener(OnSelChangedListener o) {
        listener = o;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd); // Call to the superclass method
        if (listener != null) listener.onSelChanged(selStart, selEnd);
    }
}
