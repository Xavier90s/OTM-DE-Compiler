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
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
package org.opentravel.schemacompiler.codegen.html;

import org.opentravel.schemacompiler.codegen.html.builders.DocumentationBuilder;

/**
 * A factory that returns a link given the information about it.
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class LinkFactoryImpl extends LinkFactory {

	private static final String HTML_FILE_EXT = ".html";
	
	private HtmlDocletWriter mWriter;

	public LinkFactoryImpl(HtmlDocletWriter writer) {
		mWriter = writer;
	}

	/**
	 * {@inheritDoc}
	 */
	protected LinkOutput getOutputInstance() {
		return new LinkOutputImpl();
	}

	/**
	 * {@inheritDoc}
	 */
	protected LinkOutput getObjectLink(LinkInfo linkInfo) {
		LinkInfoImpl classLinkInfo = (LinkInfoImpl) linkInfo;
		DocumentationBuilder builder = classLinkInfo.getBuilder();
		// Create a tool tip if we are linking to a class or interface. Don't
		// create one if we are linking to a member.
		String title = (classLinkInfo.getWhere() == null || classLinkInfo.getWhere()
				.length() == 0) ? getObjectToolTip(builder.getNamespace()) : "";
		StringBuilder label = new StringBuilder(
				classLinkInfo.getClassLinkLabel(mWriter.newConfiguration()));
		classLinkInfo.setDisplayLength(classLinkInfo.getDisplayLength() + label.length());
		Configuration configuration = Configuration.getInstance();
		LinkOutputImpl linkOutput = new LinkOutputImpl();
		if (configuration.isGeneratedDoc(builder)) {
			String filename = pathString(classLinkInfo);
			if (linkInfo.isLinkToSelf()
					|| !(linkInfo.getBuilder().getName() + HTML_FILE_EXT)
							.equals(mWriter.getFilename())) {
				linkOutput.append(mWriter.getHyperLinkString(filename,
						classLinkInfo.getWhere(), label.toString(),
						classLinkInfo.isStrong(), classLinkInfo.getStyleName(), title,
						classLinkInfo.getTarget()));
				return linkOutput;
			}
		}

		// Can't link so just write label.
		linkOutput.append(label.toString());
		return linkOutput;
	}

	/**
	 * Given a class, return the appropriate tool tip.
	 *
	 * @param namespace
	 *            the namespace to get the tool tip for.
	 * @return the tool tip for the namespace.
	 */
	private String getObjectToolTip(String namespace) {
		Configuration configuration = Configuration.getInstance();
		return configuration.getText("doclet.Href_Member_Title", namespace);
	}

	/**
	 * Return path to the given file name in the given package. So if the name
	 * passed is "Object.html" and the name of the package is "java.lang", and
	 * if the relative path is "../.." then returned string will be
	 * "../../java/lang/Object.html"
	 *
	 * @param linkInfo
	 *            the information about the link.
	 * @param fileName
	 *            the file name, to which path string is.
	 */
	private String pathString(LinkInfoImpl linkInfo) {
		if (linkInfo.getContext() == LinkInfoImpl.PACKAGE_FRAME) {
			// Not really necessary to do this but we want to be consistent
			// with 1.4.2 output.
			return linkInfo.getBuilder().getName() + HTML_FILE_EXT;
		}
		StringBuilder buf = new StringBuilder(mWriter.getRelativePath());
		buf.append(DirectoryManager.getPathToLibrary(
				linkInfo.getBuilder().getOwningLibrary(), linkInfo.getBuilder().getName()
						+ HTML_FILE_EXT));
		return buf.toString();
	}
}
