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
package com.biglybt.ui.swt.pifimpl;

import org.eclipse.swt.SWT;

import com.biglybt.pifimpl.local.ui.AbstractUIMessage;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.MessageBoxShell;

/**
 * @author Allan Crooks
 *
 */
public class UIMessageImpl
	extends AbstractUIMessage
{

	public UIMessageImpl() {
	}

	@Override
	public int ask() {
		final int[] result = new int[1];
		Utils.execSWTThread(new Runnable() {
			@Override
			public void run() {
				result[0] = ask0();
			}
		}, false);
		return result[0];
	}

	private int ask0() {
		int style = 0;
		switch (this.input_type) {
			case INPUT_OK_CANCEL:
				style |= SWT.CANCEL;
			case INPUT_OK:
				style |= SWT.OK;
				break;

			case INPUT_RETRY_CANCEL_IGNORE:
				style |= SWT.IGNORE;
			case INPUT_RETRY_CANCEL:
				style |= SWT.RETRY;
				style |= SWT.CANCEL;
				break;

			case INPUT_YES_NO_CANCEL:
				style |= SWT.CANCEL;
			case INPUT_YES_NO:
				style |= SWT.YES;
				style |= SWT.NO;
				break;
		}

		switch (this.message_type) {
			case MSG_ERROR:
				style |= SWT.ICON_ERROR;
				break;
			case MSG_INFO:
				style |= SWT.ICON_INFORMATION;
				break;
			case MSG_QUESTION:
				style |= SWT.ICON_QUESTION;
				break;
			case MSG_WARN:
				style |= SWT.ICON_WARNING;
				break;
			case MSG_WORKING:
				style |= SWT.ICON_WORKING;
				break;
		}

		MessageBoxShell mb = new MessageBoxShell(style, this.title,
				this.messagesAsString());
		mb.open(null);
		int result = mb.waitUntilClosed();

		switch (result) {
			case SWT.OK:
				return ANSWER_OK;
			case SWT.YES:
				return ANSWER_YES;
			case SWT.NO:
				return ANSWER_NO;
			case SWT.ABORT:
				return ANSWER_ABORT;
			case SWT.RETRY:
				return ANSWER_RETRY;
			case SWT.IGNORE:
				return ANSWER_IGNORE;
			default: // Cancel if anything else is returned.
				return ANSWER_CANCEL;
		}

	}

}
