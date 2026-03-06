package com.geysermenu.companion.menu;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a response from a menu interaction
 */
public class MenuResponse {

    private String formId;
    private UUID playerUuid;
    private ResponseType responseType;
    private int buttonId;
    private String buttonText;
    private Map<String, Object> formData;

    public MenuResponse() {}

    public enum ResponseType {
        BUTTON_CLICK,
        FORM_SUBMIT,
        CLOSED
    }

    public String getFormId() {
        return formId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public int getButtonId() {
        return buttonId;
    }

    public String getButtonText() {
        return buttonText;
    }

    public Map<String, Object> getFormData() {
        return formData;
    }

    /**
     * Check if the form was closed without action
     */
    public boolean wasClosed() {
        return responseType == ResponseType.CLOSED;
    }

    /**
     * Get a form value by component ID
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String componentId) {
        if (formData == null) return null;
        return (T) formData.get(componentId);
    }

    /**
     * Get a form value as String
     */
    public String getString(String componentId) {
        Object value = getValue(componentId);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a form value as Boolean
     */
    public Boolean getBoolean(String componentId) {
        Object value = getValue(componentId);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return null;
    }

    /**
     * Get a form value as Number
     */
    public Number getNumber(String componentId) {
        Object value = getValue(componentId);
        if (value instanceof Number) return (Number) value;
        return null;
    }
}
