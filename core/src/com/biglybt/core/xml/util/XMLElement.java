/*
 * Created on 10-Jan-2006
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.core.xml.util;

import java.io.PrintWriter;
import java.util.*;

public class XMLElement {

    /**
     *  This is what the XMLElement holds. It is either:
     *    a) A single string (text_content); or
     *    b) A collection of XMLElements.
     *
     *  Both are null at construction time - only one can be
     *  set.
     */
    protected String text_content;
    protected Collection<XMLElement> contents;

    protected Map<String,String> attributes;
    protected final String tag_name;
    protected boolean auto_order;

    public XMLElement(String tag_name) {
        this(tag_name, false);
    }

    public XMLElement(String tag_name, boolean auto_order) {
        this.text_content = null;
        this.attributes = null;
        this.contents = null;
        this.tag_name = tag_name;
        this.auto_order = auto_order;
    }

    public String getTag() {
        return tag_name;
    }

    public String getAttribute(String key) {
        if (this.attributes == null) {return null;}
        return (String)this.attributes.get(key);
    }

    public void addAttribute(String key, String value) {
        if (attributes == null) {
            this.attributes = new TreeMap<>(ATTRIBUTE_COMPARATOR);
        }
        this.attributes.put(key, value);
    }

    public void addAttribute(String key, int value) {
        this.addAttribute(key, String.valueOf(value));
    }

    public void addAttribute(String key, boolean value) {
        this.addAttribute(key, (value) ? "yes" : "no");
    }

    /**
     * Should be called setContent really - the code in the XML/HTTP plugin
     * invokes this method under this name.
     */
    public void addContent(String s) {
        if (s == null)
            throw new NullPointerException();

        if (this.contents != null)
            throw new IllegalStateException("cannot add text content to an XMLElement when it contains child XMLElement objects");

        if (this.text_content != null)
            throw new IllegalStateException("text content is already set, you cannot set it again");

        this.text_content = s;
    }

    public void addContent(XMLElement e) {
        if (e == null)
            throw new NullPointerException();

        if (this.text_content != null) {
            throw new IllegalStateException("cannot add child XMLElement when it contains text content");
        }

        /**
         * Initialise the appropriate collection as soon as we have some content.
         */
        if (this.contents == null) {
            if (!this.auto_order) {
                this.contents = new ArrayList<>();
            }
            else {
                this.contents = new TreeSet<>(CONTENT_COMPARATOR);
            }
        }

        this.contents.add(e);
    }

    public void printTo(PrintWriter pw) {
        printTo(pw, 0, false);
    }

    public void printTo(PrintWriter pw, boolean spaced_out) {
        printTo(pw, 0, spaced_out);
    }

    public void printTo(PrintWriter pw, int indent) {
        printTo(pw, indent, false);
    }

    public void printTo(PrintWriter pw, int indent, boolean spaced_out) {

        for (int i=0; i<indent; i++) {pw.print(" ");}

        /**
         * No content results in a simple self-closed tag.
         */
        if (this.attributes == null && this.contents == null && this.text_content == null) {
            pw.print("<");
            pw.print(this.tag_name);
            pw.print(" />");
            return;
        }

        pw.print("<");
        pw.print(this.tag_name);

        // Add attributes to the element.
        if (this.attributes != null) {
            Iterator<Map.Entry<String,String>> itr = this.attributes.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String,String> entry = itr.next();
                pw.print(" ");
                pw.print(entry.getKey());
                pw.print("=\"");
                pw.print(quote(entry.getValue()));
                pw.print("\"");
            }
        }

        boolean needs_indented_close = (this.contents != null);
        boolean needs_close_tag = needs_indented_close || this.text_content != null;

        needs_indented_close = needs_indented_close || spaced_out;
        needs_close_tag = needs_close_tag || spaced_out;

        if (needs_indented_close) {pw.println(">");}
        else if (needs_close_tag) {pw.print(">");}
        else {pw.print(" />");}

        // Add any text content.
        if (this.text_content != null) {
            if (spaced_out) {
                for (int i=0; i<indent+2; i++) {pw.print(" ");}
                pw.print(quote(this.text_content));
                pw.println();
            }
            else {
                pw.print(quote(this.text_content));
            }
        }

        // Add child sub-elements.
        if (this.contents != null) {
            Iterator<XMLElement> itr = this.contents.iterator();
            while (itr.hasNext()) {
                XMLElement content_element = itr.next();
                content_element.printTo(pw, indent+2, spaced_out);
            }
        }

        if (needs_indented_close) {
            for (int i=0; i<indent; i++) {pw.print(" ");}
        }

        if (needs_close_tag) {
            pw.print("</");
            pw.print(this.tag_name);
            pw.println(">");
        }
    }

    private String quote(String text) {
        text = text.replaceAll( "&", "&amp;" );
        text = text.replaceAll( ">", "&gt;" );
        text = text.replaceAll( "<", "&lt;" );
        text = text.replaceAll( "\"", "&quot;" );
        text = text.replaceAll( "--", "&#45;&#45;" );
        return text;
    }

    public XMLElement makeContent(String tag_name) {
        return this.makeContent(tag_name, false);
    }

    public XMLElement makeContent(String tag_name, boolean auto_order) {
        XMLElement content = new XMLElement(tag_name, auto_order);
        this.addContent(content);
        return content;
    }

    public void clear() {
        this.text_content = null;
        this.attributes = null;
        this.contents = null;
    }

    public void setAutoOrdering(boolean mode) {
        if (mode == this.auto_order) return;
        this.auto_order = mode;
        if (this.contents == null) return;
        Collection<XMLElement> previous_contents = contents;
        if (this.auto_order) {
            this.contents = new TreeSet<>(CONTENT_COMPARATOR);
            this.contents.addAll(previous_contents);
        }
        else {
            this.contents = new ArrayList<>(previous_contents);
        }
    }

    public String toString() {
        return "XMLElement[" + this.tag_name + "]@" + Integer.toHexString(System.identityHashCode(this));
    }

    private static final Comparator<String> ATTRIBUTE_COMPARATOR = String.CASE_INSENSITIVE_ORDER;

    private static class ContentComparator implements java.util.Comparator<XMLElement> {
        @Override
        public int compare(XMLElement xe1, XMLElement xe2) {
            if (xe1 == null || xe2 == null) throw new NullPointerException();

            /**
             * This is necessary - we don't expect to deal with two elements which
             * are fundamentally equal, but we may be asked to compare the same actual
             * object against itself when we first populate the collection which uses
             * this comparator.
             *
             * Ideally, we don't want to allow semantically equivalent objects in the
             * collection, because it's not clear what we're expected to do when we're
             * being asked to order elements. We don't expect, in the general use case,
             * to have a caller trying to add the same element twice without providing
             * a differing index attribute (what are they trying to achieve - two copies
             * at the same time or for it to be silently dropped?)
             *
             * The behaviour where we compare the same object against itself was
             * introduced here in Java 7.
             *   http://hg.openjdk.java.net/jdk7/tl/jdk/rev/bf37edb38fbb
             */
            if (xe1 == xe2) {return 0;}

            // Compare tag names.
            int result = String.CASE_INSENSITIVE_ORDER.compare(xe1.getTag(), xe2.getTag());
            if (result != 0) {return result;}

            // Tag names are the same - compare index attributes.
            int xe1_index = 0, xe2_index = 0;
            try {
                xe1_index = Integer.parseInt(xe1.getAttribute("index"));
                xe2_index = Integer.parseInt(xe2.getAttribute("index"));
            }
            catch (NullPointerException ne) {
                xe1_index = xe2_index = 0;
            }
            catch (NumberFormatException ne) {
                xe1_index = xe2_index = 0;
            }

            if (xe1_index != xe2_index) {
                return xe1_index - xe2_index;
            }

            /**
             * This is the situation we want to avoid (the one I try to describe
             * much earlier in the method) - the two elements aren't the same
             * instance and don't differ enough or have enough information to
             * describe a natural ordering.
             */
            //
            throw new IllegalArgumentException("Shouldn't be using sorting for contents if you have tags with same name and no index attribute! (tag: " + xe1.getTag() + ")");
        }
    }

    private static final Comparator<XMLElement> CONTENT_COMPARATOR = new ContentComparator();

}
