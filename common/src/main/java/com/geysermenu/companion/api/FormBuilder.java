package com.geysermenu.companion.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for creating Bedrock forms.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Simple form (button menu)
 * Form simpleForm = FormBuilder.simple()
 *     .title("My Menu")
 *     .content("Choose an option:")
 *     .button("Option 1", () -> player.sendMessage("You chose 1!"))
 *     .button("Option 2", "textures/items/diamond", () -> player.sendMessage("You chose 2!"))
 *     .onClose(() -> player.sendMessage("Menu closed"))
 *     .build();
 * 
 * // Modal form (yes/no dialog)
 * Form modalForm = FormBuilder.modal()
 *     .title("Confirm")
 *     .content("Are you sure?")
 *     .button1("Yes")
 *     .button2("No")
 *     .onConfirm(() -> doAction())
 *     .onDeny(() -> cancelAction())
 *     .build();
 * }</pre>
 */
public class FormBuilder {
    
    /**
     * Create a simple form builder (button menu)
     */
    public static SimpleFormBuilder simple() {
        return new SimpleFormBuilder();
    }
    
    /**
     * Create a modal form builder (yes/no dialog)
     */
    public static ModalFormBuilder modal() {
        return new ModalFormBuilder();
    }
    
    /**
     * Create a custom form builder (inputs, toggles, etc.)
     */
    public static CustomFormBuilder custom() {
        return new CustomFormBuilder();
    }
    
    /**
     * Represents a built form ready to be sent
     */
    public interface Form {
        String getFormType();
        String getTitle();
        String toJson();
        
        // These methods provide form data for conversion
        default String getType() { return getFormType(); }
        default String getContent() { return null; }
        default List<ButtonData> getButtons() { return List.of(); }
        default List<FormElement> getElements() { return List.of(); }
        default Consumer<com.geysermenu.companion.menu.MenuResponse> getResponseHandler() { return null; }
    }
    
    /**
     * Builder for simple forms (button menus)
     */
    public static class SimpleFormBuilder {
        private String title = "";
        private String content = "";
        private final List<ButtonData> buttons = new ArrayList<>();
        private Runnable onClose;
        private Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        
        public SimpleFormBuilder title(String title) {
            this.title = title;
            return this;
        }
        
        public SimpleFormBuilder content(String content) {
            this.content = content;
            return this;
        }
        
        /**
         * Add a button with just text
         */
        public SimpleFormBuilder button(String text, Runnable onClick) {
            buttons.add(new ButtonData(text, null, null, onClick));
            return this;
        }
        
        /**
         * Add a button with text only (no callback)
         */
        public SimpleFormBuilder button(String text) {
            buttons.add(new ButtonData(text, null, null, null));
            return this;
        }
        
        /**
         * Add a button with text and image URL
         */
        public SimpleFormBuilder button(String text, String imageUrl) {
            buttons.add(new ButtonData(text, "url", imageUrl, null));
            return this;
        }
        
        /**
         * Add a button with text and image path (resource pack)
         */
        public SimpleFormBuilder button(String text, String imagePath, Runnable onClick) {
            buttons.add(new ButtonData(text, "path", imagePath, onClick));
            return this;
        }
        
        /**
         * Add a button with text and image URL
         */
        public SimpleFormBuilder buttonWithUrl(String text, String imageUrl, Runnable onClick) {
            buttons.add(new ButtonData(text, "url", imageUrl, onClick));
            return this;
        }
        
        /**
         * Add a button that executes a command
         */
        public SimpleFormBuilder buttonCommand(String text, String command) {
            ButtonData btn = new ButtonData(text, null, null, null);
            btn.command = command;
            buttons.add(btn);
            return this;
        }
        
        /**
         * Set the handler for when the form is closed without selection
         */
        public SimpleFormBuilder onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }
        
        /**
         * Set the response handler for this form
         */
        public SimpleFormBuilder onResponse(Consumer<com.geysermenu.companion.menu.MenuResponse> handler) {
            this.responseHandler = handler;
            return this;
        }
        
        public SimpleForm build() {
            return new SimpleForm(title, content, buttons, onClose, responseHandler);
        }
    }
    
    /**
     * Builder for modal forms (yes/no dialogs)
     */
    public static class ModalFormBuilder {
        private String title = "";
        private String content = "";
        private String button1 = "Confirm";
        private String button2 = "Cancel";
        private Runnable onConfirm;
        private Runnable onDeny;
        private Runnable onClose;
        private String commandAccept;
        private String commandDeny;
        private Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        
        public ModalFormBuilder title(String title) {
            this.title = title;
            return this;
        }
        
        public ModalFormBuilder content(String content) {
            this.content = content;
            return this;
        }
        
        public ModalFormBuilder button1(String text) {
            this.button1 = text;
            return this;
        }
        
        public ModalFormBuilder button2(String text) {
            this.button2 = text;
            return this;
        }
        
        public ModalFormBuilder onConfirm(Runnable onConfirm) {
            this.onConfirm = onConfirm;
            return this;
        }
        
        public ModalFormBuilder onDeny(Runnable onDeny) {
            this.onDeny = onDeny;
            return this;
        }
        
        public ModalFormBuilder onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }
        
        public ModalFormBuilder commandAccept(String command) {
            this.commandAccept = command;
            return this;
        }
        
        public ModalFormBuilder commandDeny(String command) {
            this.commandDeny = command;
            return this;
        }
        
        /**
         * Set the response handler for this form
         */
        public ModalFormBuilder onResponse(Consumer<com.geysermenu.companion.menu.MenuResponse> handler) {
            this.responseHandler = handler;
            return this;
        }
        
        public ModalForm build() {
            return new ModalForm(title, content, button1, button2, 
                onConfirm, onDeny, onClose, commandAccept, commandDeny, responseHandler);
        }
    }
    
    /**
     * Builder for custom forms (inputs, toggles, sliders)
     */
    public static class CustomFormBuilder {
        private String title = "";
        private final List<FormElement> elements = new ArrayList<>();
        private Consumer<CustomFormResponse> onSubmit;
        private Runnable onClose;
        private Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        
        public CustomFormBuilder title(String title) {
            this.title = title;
            return this;
        }
        
        public CustomFormBuilder label(String text) {
            FormElement el = new FormElement("label", null, text, null);
            el.text = text;
            elements.add(el);
            return this;
        }
        
        public CustomFormBuilder input(String id, String label, String placeholder, String defaultValue) {
            FormElement el = new FormElement("input", id, label, defaultValue);
            el.placeholder = placeholder;
            elements.add(el);
            return this;
        }
        
        public CustomFormBuilder toggle(String id, String label, boolean defaultValue) {
            elements.add(new FormElement("toggle", id, label, defaultValue));
            return this;
        }
        
        public CustomFormBuilder slider(String id, String label, float min, float max, float step, float defaultValue) {
            FormElement el = new FormElement("slider", id, label, defaultValue);
            el.min = min;
            el.max = max;
            el.step = step;
            elements.add(el);
            return this;
        }
        
        public CustomFormBuilder dropdown(String id, String label, List<String> options, int defaultIndex) {
            FormElement el = new FormElement("dropdown", id, label, defaultIndex);
            el.options = options;
            elements.add(el);
            return this;
        }
        
        public CustomFormBuilder dropdown(String id, String label, List<String> options) {
            return dropdown(id, label, options, 0);
        }
        
        public CustomFormBuilder stepSlider(String id, String label, List<String> options, int defaultIndex) {
            FormElement el = new FormElement("stepSlider", id, label, defaultIndex);
            el.options = options;
            elements.add(el);
            return this;
        }
        
        public CustomFormBuilder stepSlider(String id, String label, List<String> options) {
            return stepSlider(id, label, options, 0);
        }
        
        public CustomFormBuilder onSubmit(Consumer<CustomFormResponse> onSubmit) {
            this.onSubmit = onSubmit;
            return this;
        }
        
        public CustomFormBuilder onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }
        
        /**
         * Set the response handler for this form
         */
        public CustomFormBuilder onResponse(Consumer<com.geysermenu.companion.menu.MenuResponse> handler) {
            this.responseHandler = handler;
            return this;
        }
        
        public CustomForm build() {
            return new CustomForm(title, elements, onSubmit, onClose, responseHandler);
        }
    }
    
    // Data classes
    
    public static class ButtonData {
        public final String text;
        public final String imageType;
        public final String imageData;
        public final Runnable onClick;
        public String command;
        
        public ButtonData(String text, String imageType, String imageData, Runnable onClick) {
            this.text = text;
            this.imageType = imageType;
            this.imageData = imageData;
            this.onClick = onClick;
        }
        
        public String getText() { return text; }
        public String getImageType() { return imageType; }
        public String getImageUrl() { return "url".equals(imageType) ? imageData : null; }
        public String getImagePath() { return "path".equals(imageType) ? imageData : null; }
    }
    
    public static class FormElement {
        public final String type;
        public final String id;
        public final String label;
        public final Object defaultValue;
        public String placeholder;
        public float min, max, step;
        public List<String> options;
        public String text;
        
        public FormElement(String type, String id, String label, Object defaultValue) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.defaultValue = defaultValue;
            this.text = label; // For label type
        }
        
        public String getType() { return type; }
        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getText() { return text != null ? text : label; }
        public String getPlaceholder() { return placeholder; }
        public String getDefaultValue() { return defaultValue instanceof String ? (String) defaultValue : null; }
        public boolean getDefaultBoolean() { return defaultValue instanceof Boolean ? (Boolean) defaultValue : false; }
        public float getDefaultFloat() { return defaultValue instanceof Number ? ((Number) defaultValue).floatValue() : 0; }
        public float getMin() { return min; }
        public float getMax() { return max; }
        public float getStep() { return step; }
        public List<String> getOptions() { return options != null ? options : List.of(); }
    }
    
    public static class SimpleForm implements Form {
        private final String title;
        private final String content;
        private final List<ButtonData> buttons;
        private final Runnable onClose;
        private final Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        
        public SimpleForm(String title, String content, List<ButtonData> buttons, Runnable onClose,
                         Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler) {
            this.title = title;
            this.content = content;
            this.buttons = buttons;
            this.onClose = onClose;
            this.responseHandler = responseHandler;
        }
        
        @Override
        public String getFormType() { return "simple"; }
        
        @Override
        public String getTitle() { return title; }
        
        @Override
        public String getContent() { return content; }
        
        @Override
        public List<ButtonData> getButtons() { return buttons; }
        
        public Runnable getOnClose() { return onClose; }
        
        @Override
        public Consumer<com.geysermenu.companion.menu.MenuResponse> getResponseHandler() {
            return responseHandler;
        }
        
        public void handleResponse(int buttonIndex) {
            if (buttonIndex >= 0 && buttonIndex < buttons.size()) {
                ButtonData btn = buttons.get(buttonIndex);
                if (btn.onClick != null) {
                    btn.onClick.run();
                }
            }
        }
        
        public void handleClose() {
            if (onClose != null) {
                onClose.run();
            }
        }
        
        @Override
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"form_type\":\"simple\",\"title\":\"").append(escapeJson(title))
              .append("\",\"content\":\"").append(escapeJson(content))
              .append("\",\"buttons\":[");
            
            for (int i = 0; i < buttons.size(); i++) {
                if (i > 0) sb.append(",");
                ButtonData btn = buttons.get(i);
                sb.append("{\"text\":\"").append(escapeJson(btn.text)).append("\"");
                if (btn.imageType != null && btn.imageData != null) {
                    sb.append(",\"image_type\":\"").append(btn.imageType)
                      .append("\",\"image_data\":\"").append(escapeJson(btn.imageData)).append("\"");
                }
                if (btn.command != null) {
                    sb.append(",\"command\":\"").append(escapeJson(btn.command)).append("\"");
                }
                sb.append("}");
            }
            
            sb.append("]}");
            return sb.toString();
        }
    }
    
    public static class ModalForm implements Form {
        private final String title;
        private final String content;
        private final String button1;
        private final String button2;
        private final Runnable onConfirm;
        private final Runnable onDeny;
        private final Runnable onClose;
        private final String commandAccept;
        private final String commandDeny;
        private final Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        private final List<ButtonData> buttons;
        
        public ModalForm(String title, String content, String button1, String button2,
                        Runnable onConfirm, Runnable onDeny, Runnable onClose,
                        String commandAccept, String commandDeny,
                        Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler) {
            this.title = title;
            this.content = content;
            this.button1 = button1;
            this.button2 = button2;
            this.onConfirm = onConfirm;
            this.onDeny = onDeny;
            this.onClose = onClose;
            this.commandAccept = commandAccept;
            this.commandDeny = commandDeny;
            this.responseHandler = responseHandler;
            // Create button list for Form interface
            this.buttons = List.of(new ButtonData(button1, null, null, onConfirm),
                                   new ButtonData(button2, null, null, onDeny));
        }
        
        @Override
        public String getFormType() { return "modal"; }
        
        @Override
        public String getTitle() { return title; }
        
        @Override
        public String getContent() { return content; }
        
        @Override
        public List<ButtonData> getButtons() { return buttons; }
        
        public String getButton1() { return button1; }
        
        public String getButton2() { return button2; }
        
        public String getCommandAccept() { return commandAccept; }
        
        public String getCommandDeny() { return commandDeny; }
        
        @Override
        public Consumer<com.geysermenu.companion.menu.MenuResponse> getResponseHandler() {
            return responseHandler;
        }
        
        public void handleResponse(boolean confirmed) {
            if (confirmed) {
                if (onConfirm != null) onConfirm.run();
            } else {
                if (onDeny != null) onDeny.run();
            }
        }
        
        public void handleClose() {
            if (onClose != null) onClose.run();
        }
        
        @Override
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"form_type\":\"modal\",\"title\":\"").append(escapeJson(title))
              .append("\",\"content\":\"").append(escapeJson(content))
              .append("\",\"button1\":\"").append(escapeJson(button1))
              .append("\",\"button2\":\"").append(escapeJson(button2)).append("\"");
            
            if (commandAccept != null) {
                sb.append(",\"command_accept\":\"").append(escapeJson(commandAccept)).append("\"");
            }
            if (commandDeny != null) {
                sb.append(",\"command_deny\":\"").append(escapeJson(commandDeny)).append("\"");
            }
            
            sb.append("}");
            return sb.toString();
        }
    }
    
    public static class CustomForm implements Form {
        private final String title;
        private final List<FormElement> elements;
        private final Consumer<CustomFormResponse> onSubmit;
        private final Runnable onClose;
        private final Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler;
        
        public CustomForm(String title, List<FormElement> elements,
                         Consumer<CustomFormResponse> onSubmit, Runnable onClose,
                         Consumer<com.geysermenu.companion.menu.MenuResponse> responseHandler) {
            this.title = title;
            this.elements = elements;
            this.onSubmit = onSubmit;
            this.onClose = onClose;
            this.responseHandler = responseHandler;
        }
        
        @Override
        public String getFormType() { return "custom"; }
        
        @Override
        public String getTitle() { return title; }
        
        @Override
        public List<FormElement> getElements() { return elements; }
        
        @Override
        public Consumer<com.geysermenu.companion.menu.MenuResponse> getResponseHandler() {
            return responseHandler;
        }
        
        public void handleResponse(CustomFormResponse response) {
            if (onSubmit != null) onSubmit.accept(response);
        }
        
        public void handleClose() {
            if (onClose != null) onClose.run();
        }
        
        @Override
        public String toJson() {
            // Custom form JSON generation would go here
            return "{}";
        }
    }
    
    public interface CustomFormResponse {
        String getString(String id);
        Boolean getBoolean(String id);
        Number getNumber(String id);
        Integer getSelectedIndex(String id);
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
