package com.geysermenu.companion.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu data structure to be sent to the extension
 */
public class MenuData {

    private String formId;
    private UUID targetPlayer;
    private MenuType type;
    private String title;
    private String content;
    private List<Button> buttons = new ArrayList<>();
    private List<FormComponent> components = new ArrayList<>();

    private MenuData() {}

    public static Builder builder() {
        return new Builder();
    }

    public enum MenuType {
        SIMPLE,
        MODAL,
        CUSTOM
    }

    public String getFormId() {
        return formId;
    }

    public UUID getTargetPlayer() {
        return targetPlayer;
    }

    public MenuType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public List<Button> getButtons() {
        return buttons;
    }

    public List<FormComponent> getComponents() {
        return components;
    }

    public record Button(String text, String imageUrl) {}

    public record FormComponent(
            String id,
            ComponentType type,
            String text,
            String placeholder,
            String defaultValue,
            float min,
            float max,
            float step,
            List<String> options
    ) {}

    public enum ComponentType {
        LABEL,
        INPUT,
        TOGGLE,
        SLIDER,
        DROPDOWN,
        STEP_SLIDER
    }

    public static class Builder {
        private final MenuData data = new MenuData();

        public Builder() {
            data.formId = UUID.randomUUID().toString();
        }

        public Builder formId(String formId) {
            data.formId = formId;
            return this;
        }

        public Builder target(UUID playerUuid) {
            data.targetPlayer = playerUuid;
            return this;
        }

        public Builder type(MenuType type) {
            data.type = type;
            return this;
        }

        public Builder simple() {
            data.type = MenuType.SIMPLE;
            return this;
        }

        public Builder modal() {
            data.type = MenuType.MODAL;
            return this;
        }

        public Builder custom() {
            data.type = MenuType.CUSTOM;
            return this;
        }

        public Builder title(String title) {
            data.title = title;
            return this;
        }

        public Builder content(String content) {
            data.content = content;
            return this;
        }

        public Builder button(String text) {
            data.buttons.add(new Button(text, null));
            return this;
        }

        public Builder button(String text, String imageUrl) {
            data.buttons.add(new Button(text, imageUrl));
            return this;
        }

        public Builder label(String text) {
            data.components.add(new FormComponent(
                    UUID.randomUUID().toString(),
                    ComponentType.LABEL,
                    text, "", "", 0, 0, 0, List.of()
            ));
            return this;
        }

        public Builder input(String id, String label, String placeholder, String defaultValue) {
            data.components.add(new FormComponent(
                    id, ComponentType.INPUT,
                    label, placeholder, defaultValue, 0, 0, 0, List.of()
            ));
            return this;
        }

        public Builder toggle(String id, String label, boolean defaultValue) {
            data.components.add(new FormComponent(
                    id, ComponentType.TOGGLE,
                    label, "", String.valueOf(defaultValue), 0, 0, 0, List.of()
            ));
            return this;
        }

        public Builder slider(String id, String label, float min, float max, float step, float defaultValue) {
            data.components.add(new FormComponent(
                    id, ComponentType.SLIDER,
                    label, "", String.valueOf(defaultValue), min, max, step, List.of()
            ));
            return this;
        }

        public Builder dropdown(String id, String label, List<String> options) {
            data.components.add(new FormComponent(
                    id, ComponentType.DROPDOWN,
                    label, "", "", 0, 0, 0, options
            ));
            return this;
        }

        public Builder stepSlider(String id, String label, List<String> options) {
            data.components.add(new FormComponent(
                    id, ComponentType.STEP_SLIDER,
                    label, "", "", 0, 0, 0, options
            ));
            return this;
        }

        public MenuData build() {
            if (data.type == null) {
                data.type = MenuType.SIMPLE;
            }
            if (data.title == null) {
                data.title = "Menu";
            }
            return data;
        }
    }
}
