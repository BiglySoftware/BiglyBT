/*
 * Created on 11-Nov-2006
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
 * This interface provides a mechanism to get some textual input from a user.
 *
 * <p>
 *
 * There are various methods which allow you to customise the appearance of
 * the prompt that the user receives - subclasses may provide additional
 * methods to customise the interface too.
 *
 * <p>
 *
 * Once the object has been set up, you then call {@link #prompt(UIInputReceiverListener)}
 * This will ask the user for some input based on the values previously given.
 * UIInputReceiverListener will only trigger once the user has either given
 * some validated input, or has indicated they don't want to give any input
 * (like pressing a Cancel button).
 *
 * <p>
 *
 * The {@link #hasSubmittedInput()} and {@link #getSubmittedInput()} methods can then
 * be invoked to retrieve the input (if the user has submitted any).
 *
 * <p>
 *
 * There are various methods which have a <tt>setXXX</tt> and <tt>setLocalisedXXX</tt>
 * counterparts. The <tt>setXXX</tt> methods will attempt to translate the given
 * string to a localised representation of it - the <tt>setLocalisedXXX</tt> method
 * will assume that the localisation has already been done, and leave it intact.
 *
 * <p><b>Note:</b> Only for implementation by Azureus, not plugins.</p>
 */
public interface UIInputReceiver {

	/**
	 * Sets the title for the text entry input. For some interfaces, this
	 * means that a window will be presented, and the title of the window
	 * will be the value passed here.
	 */
	public void setTitle(String title);

	/**
	 * Sets the title for the text entry input. For some interfaces, this
	 * means that a window will be presented, and the title of the window
	 * will be the value passed here.
	 */
	public void setLocalisedTitle(String title);

	/**
	 * Sets the message to display for the text entry input. This will
	 * normally be displayed near the position where the text will be
	 * entered - this method is usually used to present the user with
	 * an indication of what to enter.
	 *
	 * <p>
	 *
	 * For multiple lines, see {@link #setMessages}.
	 */
	public void setMessage(String message);

	/**
	 * Sets the message to display for the text entry input. This will
	 * normally be displayed near the position where the text will be
	 * entered - this method is usually used to present the user with
	 * an indication of what to enter.
	 *
	 * <p>
	 *
	 * For multiple lines, see {@link #setLocalisedMessages}.
	 */
	public void setLocalisedMessage(String message);

	/**
	 * Sets the message to display for the text entry input. This will
	 * normally be displayed near the position where the text will be
	 * entered - this method is usually used to present the user with
	 * an indication of what to enter.
	 *
	 * <p>
	 *
	 * The value passed here will be an array of strings - each string
	 * will be usually outputted on its own line. The last value in
	 * the array will usually be the message displayed closest to the
	 * users prompt.
	 */
	public void setMessages(String[] messages);

	/**
	 * Sets the message to display for the text entry input. This will
	 * normally be displayed near the position where the text will be
	 * entered - this method is usually used to present the user with
	 * an indication of what to enter.
	 *
	 * <p>
	 *
	 * The value passed here will be an array of strings - each string
	 * will be usually outputted on its own line. The last value in
	 * the array will usually be the message displayed closest to the
	 * users prompt.
	 */
	public void setLocalisedMessages(String[] messages);

	/**
	 * This sets a value to be displayed as pre-entered text for the
	 * input. This may be called if the caller wants to suggest a
	 * value for the user to use, or if the caller wants to provide
	 * a previous value (for example).
	 *
	 * <p>
	 *
	 * The text may appear in the same location as the text should
	 * be entered (allowing it to be directly overwritten or submitted
	 * immediately) - but some interfaces may not support this.
	 *
	 * <p>
	 *
	 * A flag should be passed indicating whether the pre-entered text
	 * is being entered as a suggestion for a value, or whether it is
	 * an old value being currently stored. Some interfaces may choose
	 * to differentiate between the two.
	 *
	 * @param text The text to pre-enter.
	 * @param as_suggested <tt>true</tt> if the value is a suggested
	 *   input value, <tt>false</tt> if it is an old value.
	 */
	public void setPreenteredText(String text, boolean as_suggested);

	/**
	 * Indicates whether to allow multi-line input.
	 * Default behaviour is to not allow multiple lines.
	 */
	public void setMultiLine(boolean multiline);

	/**
	 * Indicates whether to keep whitespace are kept when input is entered,
	 * or whether to strip it out. Default behaviour is to strip whitespace.
	 */
	public void maintainWhitespace(boolean keep_whitespace);

	/**
	 * Indicates whether blank input can be entered.
	 */
	public void allowEmptyInput(boolean empty_input);

	/**
	 * Sets the UIInputValidator for this object. This allows an external
	 * object to validate or reject input submitted by the user.
	 *
	 * <p>
	 *
	 * By default, there is no input validator associated with a
	 * UIInputReceiver, meaning all input is allowed.
	 *
	 * @see UIInputValidator
	 */
	public void setInputValidator(UIInputValidator validator);

	/**
	 * This prompts the user for input and returns immediately.  When the user
	 * has closed the input ui, the {@link UIInputReceiverListener} will
	 * be triggered
	 *
	 * @param receiver_listener
	 * @since 4.2.0.9
	 */
	public void prompt(UIInputReceiverListener receiver_listener);

	/**
	 * Returns <tt>true</tt> if the user submitted any data.
	 */
	public boolean hasSubmittedInput();

	/**
	 * Returns the string if the user submitted any data - you should check
	 * for this by calling {@link #hasSubmittedInput()} first.
	 */
	public String getSubmittedInput();

	/**
	 * set the maximum number of characters the user can type
	 *
	 * @since 4.3.1.5
	 */
	public void setTextLimit(int limit);
	
	/**
	 * Enables a long-press on cancel button to be treated as an escape - useful for callers that implement special escape handling
	 * such as 'escape closes this and any other similar outstanding dialogs', e.g. when renaming a number of files this can avoid
	 * the user having to explicitly cancel each rename if they decide to abort the operation 
	 * @param b
	 */
	
	public void setEnableSpecialEscapeHandling( boolean b );
	
	public boolean userHitEscape();
}
