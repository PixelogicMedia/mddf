/**
 * Created Jun 29, 2016 
 * Copyright Motion Picture Laboratories, Inc. 2016
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of 
 * this software and associated documentation files (the "Software"), to deal in 
 * the Software without restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of 
 * the Software, and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS 
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR 
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER 
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.movielabs.mddflib.logging;

import java.io.File;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 * @author L. Levin, Critical Architectures LLC
 */
public abstract class LogEntry extends DefaultMutableTreeNode {
	/**
	 * 
	 */
	protected String tag;
	protected File myFile;

	/**
	 * @param label
	 */
	public LogEntry(String label) {
		super(label);
	}

	/**
	 * 
	 */
	public LogEntry() {
	}

	/**
	 * @return the tag
	 */
	public String getTagAsText() {
		return tag;
	}

	/**
	 * @param tag
	 *            the tag to set
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	public File getFile() {
		return myFile;
	}

//	public String getFileLabel() {
//		TreeNode[] myPath = this.getPath();
//		if (myPath.length < 1) {
//			return this.toString();
//		} else {
//			LogEntryFolder fileFolder = (LogEntryFolder)myPath[0];
//			return fileFolder.getLabel();
//		}
//	}
}