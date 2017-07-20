/*
 * Created on 13-Nov-2006
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
package com.biglybt.pifimpl.local.ui;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.ui.UIInputValidator;

/**
 * Abstract class to make it easy for class to implement UIInputReceiver classes.
 *
 * The common convention is that it has all the necessary set methods needed, and
 * all settings passed are accessible through protected attributes.
 *
 * Checks are made to ensure only certain methods are called at the right time.
 */
public abstract class AbstractUIInputReceiver implements UIInputReceiver {

	public AbstractUIInputReceiver() {
	}

	private boolean prompted = false;

	// Helper methods.
	protected final void assertPrePrompt() {
		if (prompted) {
			throw new RuntimeException("cannot invoke after prompt has been called");
		}
	}

	protected final void assertPostPrompt() {
		if (!prompted) {
			throw new RuntimeException("cannot before after prompt has been called");
		}
	}

	@Override
	public void setLocalisedMessage(String message) {
		this.setLocalisedMessages(new String[]{message});
	}

	protected String[] messages = new String[0];

	@Override
	public void setLocalisedMessages(String[] messages) {
		assertPrePrompt();
		this.messages = messages;
	}

	protected String title = null;

	@Override
	public void setLocalisedTitle(String title) {
		assertPrePrompt();
		this.title = title;
	}

	@Override
	public void setMessage(String message) {
		this.setLocalisedMessage(this.localise(message));
	}

	@Override
	public void setMessages(String[] messages) {
		String[] new_messages = new String[messages.length];
		for (int i=0; i<new_messages.length; i++) {
			new_messages[i] = this.localise(messages[i]);
		}
		this.setLocalisedMessages(new_messages);
	}

	protected boolean multiline_mode = false;

	@Override
	public void setMultiLine(boolean multiline) {
		assertPrePrompt();
		this.multiline_mode = multiline;
	}

	protected String preentered_text = null;
	//protected boolean preentered_text_is_old_value = false;

	@Override
	public void setPreenteredText(String text, boolean as_suggested) {
		assertPrePrompt();
		this.preentered_text = text;
		//this.preentered_text_is_old_value = !as_suggested;
	}

	@Override
	public void setTitle(String title) {
		this.setLocalisedTitle(this.localise(title));
	}

	protected UIInputValidator validator = null;

	@Override
	public void setInputValidator(UIInputValidator validator) {
		assertPrePrompt();
		this.validator = validator;
	}

	private boolean result_recorded = false;

	protected UIInputReceiverListener receiver_listener;

	protected boolean isResultRecorded() {
		return result_recorded;
	}

	@Override
	public final void prompt(UIInputReceiverListener receiver_listener) {
		assertPrePrompt();
		this.receiver_listener = receiver_listener;
		this.promptForInput();
	}

	public final void triggerReceiverListener() {
		if (!result_recorded) {
			throw new RuntimeException(this.toString() + " did not record a result.");
		}
		this.prompted = true;
		if (receiver_listener != null) {
			receiver_listener.UIInputReceiverClosed(this);
		}
	}

	private boolean result_input_submitted = false;
	private String result_input = null;

	/**
	 * Subclasses must override this method to receive input from the user.
	 *
	 * This method must call either recordUserInput or recordUserAbort before
	 * returning.
	 */
	protected abstract void promptForInput();

	protected final void recordUserInput(String input) {
		this.result_recorded = true;
		this.result_input_submitted = true;
		this.result_input = input;

		// The subclass should strip the output before it sets
		// the value here - but just in case they forget, we remember.
		//
		// Subclasses should do it that the validator can validate the stripped string.
		if (!this.maintain_whitespace) {
			this.result_input = input.trim();
		}
	}

	protected final void recordUserAbort() {
		this.result_recorded = true;
		this.result_input_submitted = false;
		this.result_input = null;
	}

	@Override
	public boolean hasSubmittedInput() {
		assertPostPrompt();
		return this.result_input_submitted;
	}

	@Override
	public String getSubmittedInput() {
		assertPostPrompt();
		return this.result_input;
	}

	protected boolean maintain_whitespace = false;

	@Override
	public void maintainWhitespace(boolean keep_whitespace) {
		this.maintain_whitespace = keep_whitespace;
	}

	protected boolean allow_empty_input = true;

	@Override
	public void allowEmptyInput(boolean empty_input) {
		this.allow_empty_input = empty_input;
	}

	protected final String localise(String key) {
		return MessageText.getString(key);
	}

}
