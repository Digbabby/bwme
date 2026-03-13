package com.example.bwme;

import android.text.Editable;
import android.text.TextWatcher;

public class SimpleTextWatcher implements TextWatcher {
    public interface OnChanged { void onChanged(); }
    private final OnChanged cb;
    public SimpleTextWatcher(OnChanged cb) { this.cb = cb; }
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override public void afterTextChanged(Editable s) { if (cb != null) cb.onChanged(); }
}