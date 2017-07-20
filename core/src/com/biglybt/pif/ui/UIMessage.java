/*
 * Created on 12 Apr 2008
 * Created by Allan Crooks
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.pif.ui;

/**
 * This interface provides a mechanism to display a message to the user,
 * potentially to ask the user a question.
 *
 * <p>
 *
 * There are four main elements to set:
 * <ul>
 *   <li>the <i>message</i> type - defaults to <tt>MSG_NONE</tt>.
 *   <li>the <i>input</i> type - defaults to <tt>INPUT_OK</tt>.
 *   <li>the message title.
 *   <li>the message text.
 * </ul>
 *
 * In the case of the main SWT interface, the use of this object will present
 * a message box to a user. The message type indicates what sort of icon to display,
 * the input type indicates what buttons are present, the message title is the message
 * box title and the message text is the main message to be displayed.
 *
 * <p>
 *
 * There are various methods which have a <tt>setXXX</tt> and <tt>setLocalisedXXX</tt>
 * counterparts. The <tt>setXXX</tt> methods will attempt to translate the given
 * string to a localised representation of it - the <tt>setLocalisedXXX</tt> method
 * will assume that the localisation has already been done, and leave it intact.
 *
 * <p><b>Note:</b> Only for implementation by Azureus, not plugins.</p>
 *
 * @author Allan Crooks
 * @since 3.0.5.3
 */
public interface UIMessage {

	public int MSG_NONE = 0;
	public int MSG_ERROR = 1;
	public int MSG_INFO = 2;
	public int MSG_WARN = 3;
	public int MSG_QUESTION = 4;
	public int MSG_WORKING = 5;

	public int INPUT_OK = 0;
	public int INPUT_OK_CANCEL = 1;
	public int INPUT_YES_NO = 2;
	public int INPUT_YES_NO_CANCEL = 3;
	public int INPUT_RETRY_CANCEL = 4;
	public int INPUT_RETRY_CANCEL_IGNORE = 5;

	public int ANSWER_OK = 0;
	public int ANSWER_CANCEL = 1;
	public int ANSWER_YES = 2;
	public int ANSWER_NO = 3;
	public int ANSWER_RETRY = 4;
	public int ANSWER_IGNORE = 5;
	public int ANSWER_ABORT = ANSWER_CANCEL;

	/**
	 * Sets what type of message to display - use one of the <tt>MSG_</tt>
	 * constants defined here.
	 */
	public void setMessageType(int msg_type);

	/**
	 * Sets what type of input is allowed - use one of the <tt>INPUT_</tt>
	 * constants defined here.
	 */
	public void setInputType(int input_type);

	/**
	 * Sets the title for the message. For some interfaces, this
	 * means that a window will be presented, and the title of the window
	 * will be the value passed here.
	 */
	public void setTitle(String title);

	/**
	 * Sets the title for the message. For some interfaces, this
	 * means that a window will be presented, and the title of the window
	 * will be the value passed here.
	 */
	public void setLocalisedTitle(String title);

	/**
	 * Sets the main message to display the user.
	 *
	 * For multiple lines, see {@link #setMessages}.
	 */
	public void setMessage(String message);

	/**
	 * Sets the main message to display the user.
	 *
	 * For multiple lines, see {@link #setLocalisedMessages}.
	 */
	public void setLocalisedMessage(String message);

	/**
	 * Sets the main message to display the user.
	 *
	 * <p>
	 *
	 * The value passed here will be an array of strings - each string
	 * will be usually outputted on its own line.
	 */
	public void setMessages(String[] messages);

	/**
	 * Sets the main message to display the user.
	 *
	 * <p>
	 *
	 * The value passed here will be an array of strings - each string
	 * will be usually outputted on its own line.
	 */
	public void setLocalisedMessages(String[] messages);

	/**
	 * This displays the message to the user. The way that the user
	 * acknowledges will be the method result - which will be one of
	 * the <tt>ANSWER_</tt> constants defined here.
	 */
	public int ask();

}
