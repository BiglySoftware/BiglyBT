/*
 * Created on 23-May-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pif.update;

/**
 * @author parg
 *
 */

public interface
UpdateCheckerListener
{
		/**
		 * Called when the checking process has completed successfully
		 * @param checker
		 */

	public void
	completed(
		UpdateChecker	checker );

		/**
		 * Called when the checking process failed
		 * @param checker
		 */

	public void
	failed(
		UpdateChecker	checker );

		/**
		 * Called when the checking process has been cancelled
		 * @param checker
		 */

	public void
	cancelled(
		UpdateChecker	checker );
}
