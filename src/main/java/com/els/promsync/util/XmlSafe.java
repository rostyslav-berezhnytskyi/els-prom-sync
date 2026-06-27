package com.els.promsync.util;

public final class XmlSafe {

    private XmlSafe() {
    }

    public static String text(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String attribute(String value) {
        if (value == null) {
            return "";
        }

        return text(value)
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static String cdata(String value) {
        if (value == null) {
            return "<![CDATA[]]>";
        }

        return "<![CDATA[" + value.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }
}