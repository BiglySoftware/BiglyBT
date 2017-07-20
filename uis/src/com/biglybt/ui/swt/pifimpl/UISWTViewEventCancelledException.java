/*
 * Created on Jul 20, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.pifimpl;

/**
 * @author TuxPaper
 * @created Jul 20, 2008
 *
 */
public class UISWTViewEventCancelledException
	extends Exception
{

	/**
	 *
	 */
	private static final long serialVersionUID = -1750725255042799344L;

	/**
	 *
	 */
	public UISWTViewEventCancelledException() {
		super();
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 * @param cause
	 */
	public UISWTViewEventCancelledException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param message
	 */
	public UISWTViewEventCancelledException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param cause
	 */
	public UISWTViewEventCancelledException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

}
