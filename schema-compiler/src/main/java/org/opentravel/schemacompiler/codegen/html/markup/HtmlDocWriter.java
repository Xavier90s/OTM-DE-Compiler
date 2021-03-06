/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.opentravel.schemacompiler.codegen.html.markup;


import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.opentravel.schemacompiler.model.LibraryMember;
import org.opentravel.schemacompiler.codegen.html.builders.DocumentationBuilder;
import org.opentravel.schemacompiler.codegen.html.Configuration;
import org.opentravel.schemacompiler.codegen.html.Content;


/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used by the Sub-Classes in the package com.sun.tools.doclets.standard
 * and com.sun.tools.doclets.oneone.
 *
 * @since 1.2
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Eric.Bronson(modified for otm)
 */
public abstract class HtmlDocWriter extends HtmlWriter {

    /**
     * Constructor. Initializes the destination file name through the super
     * class HtmlWriter.
     *
     * @param filename String file name.
     */
    public HtmlDocWriter(Configuration configuration,
                         String filename) throws IOException {
        super(configuration,
              null, configuration.getDestDirName() + filename,
              configuration.getDocencoding());
    }

    public HtmlDocWriter(Configuration configuration,
                         String path, String filename) throws IOException {
        super(configuration,
              configuration.getDestDirName() + path, filename,
              configuration.getDocencoding());
    }

    /**
     * Accessor for configuration.
     */
    public abstract Configuration newConfiguration();

    /**
     * Print Html Hyper Link.
     *
     * @param link String name of the file.
     * @param where Position of the link in the file. Character '#' is not
     * needed.
     * @param label Tag for the link.
     * @param STRONG  Boolean that sets label to STRONG.
     */
    public void printHyperLink(String link, String where,
                               String label, boolean strong) {
        print(getHyperLinkString(link, where, label, strong, "", "", ""));
    }

    /**
     * Print Html Hyper Link.
     *
     * @param link String name of the file.
     * @param where Position of the link in the file. Character '#' is not
     * needed.
     * @param label Tag for the link.
     */
    public void printHyperLink(String link, String where, String label) {
        printHyperLink(link, where, label, false);
    }

    /**
     * Print Html Hyper Link.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     * needed.
     * @param label      Tag for the link.
     * @param STRONG       Boolean that sets label to STRONG.
     * @param stylename  String style of text defined in style sheet.
     */
    public void printHyperLink(String link, String where,
                               String label, boolean strong,
                               String stylename) {
        print(getHyperLinkString(link, where, label, strong, stylename, "", ""));
    }

    /**
     * Return Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     * needed.
     * @param label      Tag for the link.
     * @param STRONG       Boolean that sets label to STRONG.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(String link, String where,
                               String label, boolean strong) {
        return getHyperLinkString(link, where, label, strong, "", "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @param STRONG       Boolean that sets label to STRONG.
     * @param stylename  String style of text defined in style sheet.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(String link, String where,
                               String label, boolean strong,
                               String stylename) {
        return getHyperLinkString(link, where, label, strong, stylename, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @return a content tree for the hyper link
     */
    public Content getHyperLink(String link, String where,
                               Content label) {
        return getHyperLink(link, where, label, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @param STRONG       Boolean that sets label to STRONG.
     * @param stylename  String style of text defined in style sheet.
     * @param title      String that describes the link's content for accessibility.
     * @param target     Target frame.
     * @return String    Hyper Link.
     */
    public String getHyperLinkString(String link, String where,
                               String label, boolean strong,
                               String stylename, String title, String target) {
        StringBuilder retlink = new StringBuilder();
        retlink.append("<a href=\"");
        retlink.append(link);
        if (where != null && where.length() != 0) {
            retlink.append("#");
            retlink.append(where);
        }
        retlink.append("\"");
        if (title != null && title.length() != 0) {
            retlink.append(" title=\"" + title + "\"");
        }
        if (target != null && target.length() != 0) {
            retlink.append(" target=\"" + target + "\"");
        }
        retlink.append(">");
        if (stylename != null && stylename.length() != 0) {
            retlink.append("<FONT CLASS=\"");
            retlink.append(stylename);
            retlink.append("\">");
        }
        if (strong) {
            retlink.append("<span class=\"STRONG\">");
        }
        retlink.append(label);
        if (strong) {
            retlink.append("</span>");
        }
        if (stylename != null && stylename.length() != 0) {
            retlink.append("</FONT>");
        }
        retlink.append("</a>");
        return retlink.toString();
    }

    /**
     * Get Html Hyper Link.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @param title      String that describes the link's content for accessibility.
     * @param target     Target frame.
     * @return a content tree for the hyper link.
     */
    public Content getHyperLink(String link, String where,
            Content label, String title, String target) {
        if (where != null && where.length() != 0) {
            link += "#" + where;
        }
        HtmlTree anchor = HtmlTree.a(link, label);
        if (title != null && title.length() != 0) {
            anchor.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            anchor.addAttr(HtmlAttr.TARGET, target);
        }
        return anchor;
    }

    /**
     * Get a hyperlink to a file.
     *
     * @param link String name of the file
     * @param label Label for the link
     * @return a content for the hyperlink to the file
     */
    public Content getHyperLink(String link, Content label) {
        return getHyperLink(link, "", label);
    }

    /**
     * Get link string without positioning in the file.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @return Strign    Hyper link.
     */
    public String getHyperLinkString(String link, String label) {
        return getHyperLinkString(link, "", label, false);
    }

    /**
     * Print the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public void printNamespaceName(LibraryMember cd) {
        print(getNamespace(cd));
    }

    /**
     * Get the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public String getNamespace(LibraryMember cd) {
        String namespace = cd.getNamespace();
        if (namespace.length() > 0) {
            namespace += ":";
            return namespace;
        }
        return "";
    }

    /**
     * Print the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public void printNamespaceName(DocumentationBuilder cd) {
        print(getNamespace(cd));
    }

    /**
     * Get the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public String getNamespace(DocumentationBuilder cd) {
        String namespace = cd.getNamespace();
        if (namespace.length() > 0) {
            namespace += ":";
            return namespace;
        }
        return "";
    }
    /**
     * Keep track of member details list. Print the definition list start tag
     * if it is not printed yet.
     */
    public void printMemberDetailsListStartTag () {
        if (!getMemberDetailsListPrinted()) {
            dl();
            memberDetailsListPrinted = true;
        }
    }

    /**
     * Print the definition list end tag if the list start tag was printed.
     */
    public void printMemberDetailsListEndTag () {
        if (getMemberDetailsListPrinted()) {
            dlEnd();
            memberDetailsListPrinted = false;
        }
    }

    public boolean getMemberDetailsListPrinted() {
        return memberDetailsListPrinted;
    }

    /**
     * Print the frameset version of the Html file header.
     * Called only when generating an HTML frameset file.
     *
     * @param title Title of this HTML document
     * @param frameset the frameset to be added to the HTML document
     */
    public void printFramesetDocument(String title, Content frameset) {
        Content htmlDocType = DocType.newFrameset();
        Content htmlComment = new Comment(configuration.getText("doclet.New_Page"));
        Content head = new HtmlTree(HtmlTag.HEAD);
        
            Content headComment = new Comment("Generated by javadoc on " + today());
            head.addContent(headComment);
        
        Content windowTitle = HtmlTree.title(new StringContent(title));
        head.addContent(windowTitle);
        head.addContent(getFramesetJavaScript());
        Content htmlTree = HtmlTree.html(configuration.getLocale().getLanguage(),
                head, frameset);
        Content htmlDocument = new HtmlDocument(htmlDocType,
                htmlComment, htmlTree);
        print(htmlDocument.toString());
    }

    /**
     * Print the appropriate spaces to format the class tree in the class page.
     *
     * @param len   Number of spaces.
     */
    public String spaces(int len) {
        StringBuilder space = new StringBuilder();

        for (int i = 0; i < len; i++) {
            space.append(" ");
        }
        return space.toString();
    }

    /**
     * Print the closing &lt;/body&gt; and &lt;/html&gt; tags.
     */
    public void printBodyHtmlEnd() {
        println();
        bodyEnd();
        htmlEnd();
    }

    /**
     * Calls {@link #printBodyHtmlEnd()} method.
     */
    public void printFooter() {
        printBodyHtmlEnd();
    }

    /**
     * Print closing &lt;/html&gt; tag.
     */
    public void printFrameFooter() {
        htmlEnd();
    }

    /**
     * Print ten non-breaking spaces("&#38;nbsp;").
     */
    public void printNbsps() {
        print("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
    }

    /**
     * Get the day and date information for today, depending upon user option.
     *
     * @return String Today.
     * @see java.util.Calendar
     * @see java.util.GregorianCalendar
     * @see java.util.TimeZone
     */
    public String today() {
        Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
        return calendar.getTime().toString();
    }
}
