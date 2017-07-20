/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.progress;

/**
 * A simple class for a message
 * @author knguyen
 *
 */
public class ProgressReportMessage
	implements IMessage, IProgressReportConstants
{

	private String value = "";

	private int type;

	/**
	 * Create a message for the given value and type; message type can by any one of:
	 * <ul>
	 * <li> <code>IProgressReportConstants.MSG_TYPE_ERROR</code> -- an error message</li>
	 * <li> <code>IProgressReportConstants.MSG_TYPE_INFO</code> -- a general informational message</li>
	 * <li> <code>IProgressReportConstants.MSG_TYPE_LOG</code> -- a log message; for messages that are more detailed and verbose</li>
	 * </ul>
	 * @param value
	 * @param type
	 */
	public ProgressReportMessage(String value, int type) {
		this.value = value;

		switch (type) {
			case MSG_TYPE_ERROR:
			case MSG_TYPE_INFO:
				this.type = type;
				break;
			default:
				this.type = MSG_TYPE_LOG;
		}
	}

	@Override
	public String getValue() {
		return value;
	}

	@Override
	public int getType() {
		return type;
	}

	public boolean isError() {
		return type == MSG_TYPE_ERROR;
	}

	public boolean isInfo() {
		return type == MSG_TYPE_INFO;
	}

	public boolean isLog() {
		return type == MSG_TYPE_LOG;
	}
}
