/*
 * Copyright (c) 2002-2016 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.javascript.host.css;

import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.CSS_SUPPORTS_BEHAVIOR_PROPERTY;
import static com.gargoylesoftware.htmlunit.BrowserVersionFeatures.JS_STYLE_SET_PROPERTY_IMPORTANT_IGNORES_CASE;
import static com.gargoylesoftware.htmlunit.javascript.host.css.StyleAttributes.Definition.BEHAVIOR;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.javascript.SimpleScriptObject;
import com.gargoylesoftware.htmlunit.javascript.host.Element2;
import com.gargoylesoftware.htmlunit.javascript.host.css.StyleAttributes.Definition;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLElement2;
import com.gargoylesoftware.js.nashorn.ScriptUtils;
import com.gargoylesoftware.js.nashorn.internal.objects.annotations.Getter;
import com.gargoylesoftware.js.nashorn.internal.objects.annotations.Setter;
import com.gargoylesoftware.js.nashorn.internal.runtime.Context;
import com.gargoylesoftware.js.nashorn.internal.runtime.PrototypeObject;
import com.gargoylesoftware.js.nashorn.internal.runtime.ScriptFunction;

public class CSSStyleDeclaration2 extends SimpleScriptObject {

    private static final Log LOG = LogFactory.getLog(CSSStyleDeclaration2.class);
    private static final Map<String, String> CSSColors_ = new HashMap<>();

    /** CSS important property constant. */
    protected static final String PRIORITY_IMPORTANT = "important";

    /** Used to parse URLs. */
    private static final MessageFormat URL_FORMAT = new MessageFormat("url({0})");

    /** The element to which this style belongs. */
    private Element2 jsElement_;

    /** The wrapped CSSStyleDeclaration (if created from CSSStyleRule). */
    private org.w3c.dom.css.CSSStyleDeclaration styleDeclaration_;

    /** Cache for the styles. */
    private String styleString_ = new String();
    private Map<String, StyleElement> styleMap_;

    /** The current style element index. */
    private long currentElementIndex_;

    static {
        CSSColors_.put("aqua", "rgb(0, 255, 255)");
        CSSColors_.put("black", "rgb(0, 0, 0)");
        CSSColors_.put("blue", "rgb(0, 0, 255)");
        CSSColors_.put("fuchsia", "rgb(255, 0, 255)");
        CSSColors_.put("gray", "rgb(128, 128, 128)");
        CSSColors_.put("green", "rgb(0, 128, 0)");
        CSSColors_.put("lime", "rgb(0, 255, 0)");
        CSSColors_.put("maroon", "rgb(128, 0, 0)");
        CSSColors_.put("navy", "rgb(0, 0, 128)");
        CSSColors_.put("olive", "rgb(128, 128, 0)");
        CSSColors_.put("purple", "rgb(128, 0, 128)");
        CSSColors_.put("red", "rgb(255, 0, 0)");
        CSSColors_.put("silver", "rgb(192, 192, 192)");
        CSSColors_.put("teal", "rgb(0, 128, 128)");
        CSSColors_.put("white", "rgb(255, 255, 255)");
        CSSColors_.put("yellow", "rgb(255, 255, 0)");
    }

    CSSStyleDeclaration2() {
        
    }

    CSSStyleDeclaration2(final Element2 element) {
        initialize(element);
    }

    public static CSSStyleDeclaration2 constructor(final boolean newObj, final Object self) {
        final CSSStyleDeclaration2 host = new CSSStyleDeclaration2();
        host.setProto(Context.getGlobal().getPrototype(host.getClass()));
        return host;
    }

    /**
     * Initializes the object.
     * @param htmlElement the element that this style describes
     */
    void initialize(final Element2 element) {
        // Initialize.
        jsElement_ = element;
        setDomNode(element.getDomNodeOrNull(), false);

        // If an IE behavior was specified in the style, apply the behavior.
        if (getBrowserVersion().hasFeature(CSS_SUPPORTS_BEHAVIOR_PROPERTY)
            && element instanceof HTMLElement2) {
            final HTMLElement2 htmlElement = (HTMLElement2) element;
            final String behavior = getStyleAttribute(BEHAVIOR);
            if (StringUtils.isNotBlank(behavior)) {
                try {
                    final Object[] url = URL_FORMAT.parse(behavior);
                    if (url.length > 0) {
                        htmlElement.addBehavior((String) url[0]);
                    }
                }
                catch (final ParseException e) {
                    LOG.warn("Invalid behavior: '" + behavior + "'.");
                }
            }
        }
    }

    /**
     * Returns the element to which this style belongs.
     * @return the element to which this style belongs
     */
    protected Element2 getElement() {
        return jsElement_;
    }

    /**
     * Get the value for the style attribute.
     * @param definition the definition
     * @return the value
     */
    public final String getStyleAttribute(final Definition definition) {
        return getStyleAttribute(definition, true);
    }

    /**
     * Get the value for the style attribute.
     * @param definition the definition
     * @param getDefaultValueIfEmpty whether to get the default value if empty or not
     * @return the value
     */
    public String getStyleAttribute(final Definition definition, final boolean getDefaultValueIfEmpty) {
        return getStyleAttributeImpl(definition.getAttributeName());
    }

    private String getStyleAttributeImpl(final String string) {
        if (styleDeclaration_ != null) {
            return styleDeclaration_.getPropertyValue(string);
        }
        final StyleElement element = getStyleElement(string);
        if (element != null && element.getValue() != null) {
            final String value = element.getValue();
            if (!value.contains("url")
                    && getBrowserVersion().hasFeature(JS_STYLE_SET_PROPERTY_IMPORTANT_IGNORES_CASE)) {
                return value.toLowerCase(Locale.ROOT);
            }
            return value;
        }
        return "";
    }

    /**
     * Determines the StyleElement for the given name.
     *
     * @param name the name of the requested StyleElement
     * @return the StyleElement or null if not found
     */
    protected StyleElement getStyleElement(final String name) {
        final Map<String, StyleElement> map = getStyleMap();
        if (map != null) {
            return map.get(name);
        }
        return null;
    }

    /**
     * Determines the StyleElement for the given name.
     * This ignores the case of the name.
     *
     * @param name the name of the requested StyleElement
     * @return the StyleElement or null if not found
     */
    private StyleElement getStyleElementCaseInSensitive(final String name) {
        final Map<String, StyleElement> map = getStyleMap();
        for (final Map.Entry<String, StyleElement> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns a sorted map containing style elements, keyed on style element name. We use a
     * {@link LinkedHashMap} map so that results are deterministic and are thus testable.
     *
     * @return a sorted map containing style elements, keyed on style element name
     */
    private Map<String, StyleElement> getStyleMap() {
        if (jsElement_ == null) {
            return Collections.emptyMap();
        }
        final String styleAttribute = jsElement_.getDomNodeOrDie().getAttribute("style");
        if (styleString_ == styleAttribute) {
            return styleMap_;
        }

        final Map<String, StyleElement> styleMap = new LinkedHashMap<>();
        if (DomElement.ATTRIBUTE_NOT_DEFINED == styleAttribute || DomElement.ATTRIBUTE_VALUE_EMPTY == styleAttribute) {
            styleMap_ = styleMap;
            styleString_ = styleAttribute;
            return styleMap_;
        }

        for (final String token : StringUtils.split(styleAttribute, ';')) {
            final int index = token.indexOf(":");
            if (index != -1) {
                final String key = token.substring(0, index).trim().toLowerCase(Locale.ROOT);
                String value = token.substring(index + 1).trim();
                String priority = "";
                if (StringUtils.endsWithIgnoreCase(value, "!important")) {
                    priority = PRIORITY_IMPORTANT;
                    value = value.substring(0, value.length() - 10);
                    value = value.trim();
                }
                final StyleElement element = new StyleElement(key, value, priority,
                        SelectorSpecificity.FROM_STYLE_ATTRIBUTE, getCurrentElementIndex());
                styleMap.put(key, element);
            }
        }

        styleMap_ = styleMap;
        styleString_ = styleAttribute;
        return styleMap_;
    }

    /**
     * Returns the current style element index. An index is assigned to each style element so that
     * we can determine which style elements have precedence over others.
     *
     * This method also takes care of incrementing the index for the next use.
     *
     * @return the current style element index
     */
    protected long getCurrentElementIndex() {
        return currentElementIndex_++;
    }

    /**
     * Returns the actual text of the style.
     * @return the actual text of the style
     */
    @Getter
    public String getCssText() {
        return jsElement_.getDomNodeOrDie().getAttribute("style");
    }

    /**
     * Sets the actual text of the style.
     * @param value the new text
     */
    @Setter
    public void setCssText(final String value) {
        jsElement_.getDomNodeOrDie().setAttribute("style", value);
    }

    private static MethodHandle staticHandle(final String name, final Class<?> rtype, final Class<?>... ptypes) {
        try {
            return MethodHandles.lookup().findStatic(CSSStyleDeclaration2.class,
                    name, MethodType.methodType(rtype, ptypes));
        }
        catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class FunctionConstructor extends ScriptFunction {
        public FunctionConstructor() {
            super("CSSStyleDeclaration", 
                    staticHandle("constructor", CSSStyleDeclaration2.class, boolean.class, Object.class),
                    null);
            final Prototype prototype = new Prototype();
            PrototypeObject.setConstructor(prototype, this);
            setPrototype(prototype);
        }
    }

    public static final class Prototype extends PrototypeObject {
        Prototype() {
            ScriptUtils.initialize(this);
        }

        public String getClassName() {
            return "CSSStyleDeclaration";
        }
    }

    /**
     * Contains information about a single style element, including its name, its value, and an index which
     * can be compared against other indices in order to determine precedence.
     */
    protected static class StyleElement implements Comparable<StyleElement>, Serializable {
        private final String name_;
        private final String value_;
        private final String priority_;
        private final long index_;
        private final SelectorSpecificity specificity_;

        /**
         * Creates a new instance.
         * @param name the style element's name
         * @param value the style element's value
         * @param priority the style element's priority like "important"
         * @param specificity the specificity of the rule providing this style information
         * @param index the style element's index
         */
        protected StyleElement(final String name, final String value, final String priority,
                final SelectorSpecificity specificity, final long index) {
            name_ = name;
            value_ = value;
            priority_ = priority;
            index_ = index;
            specificity_ = specificity;
        }

        /**
         * Creates a new instance.
         * @param name the style element's name
         * @param value the style element's value
         * @param index the style element's index
         */
        protected StyleElement(final String name, final String value, final long index) {
            this(name, value, "", SelectorSpecificity.FROM_STYLE_ATTRIBUTE, index);
        }

        /**
         * Creates a new default instance.
         * @param name the style element's name
         * @param value the style element's value
         */
        protected StyleElement(final String name, final String value) {
            this(name, value, Long.MIN_VALUE);
        }

        /**
         * Returns the style element's name.
         * @return the style element's name
         */
        public String getName() {
            return name_;
        }

        /**
         * Returns the style element's value.
         * @return the style element's value
         */
        public String getValue() {
            return value_;
        }

        /**
         * Returns the style element's priority.
         * @return the style element's priority
         */
        public String getPriority() {
            return priority_;
        }

        /**
         * Returns the specificity of the rule specifying this element.
         * @return the specificity
         */
        public SelectorSpecificity getSpecificity() {
            return specificity_;
        }

        /**
         * Returns the style element's index.
         * @return the style element's index
         */
        public long getIndex() {
            return index_;
        }

        /**
         * Returns {@code true} if this style element contains a default value. This method isn't
         * currently used anywhere because default style elements are applied before non-default
         * style elements, so the natural ordering results in correct precedence rules being applied
         * (i.e. default style elements don't override non-default style elements) without the need
         * for special checks.
         * @return {@code true} if this style element contains a default value
         */
        public boolean isDefault() {
            return index_ == Long.MIN_VALUE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "[" + index_ + "]" + name_  + "=" + value_;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(final StyleElement e) {
            if (e != null) {
                final long styleIndex = e.index_;
                // avoid conversion to long
                return (index_ < styleIndex) ? -1 : (index_ == styleIndex) ? 0 : 1;
            }
            return 1;
        }
    }

}
