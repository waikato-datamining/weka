package weka.gui;

import java.beans.PropertyEditorSupport;

/**
 * Simple property editor for Strings that avoids relying on
 * com.sun.beans.editors.StringEditor (which is inaccessible under JPMS unless opened).
 */
public class WekaStringEditor extends PropertyEditorSupport {

    @Override
    public String getAsText() {
        Object v = getValue();
        return (v == null) ? "" : v.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(text);
    }

    @Override
    public boolean supportsCustomEditor() {
        return false;
    }
}