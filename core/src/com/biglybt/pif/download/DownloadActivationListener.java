/*
 * Created on 13 Jul 2006
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

package com.biglybt.pif.download;

public interface
DownloadActivationListener
{
		/**
		 * A request has been made to activate the download. Not this is only fired on an increase in the
		 * activation request count, not on a decrease. To get a current snapshot of this use the method
		 * getActivationState in Download
		 * @param event
		 * @return return true if the download will be activated, false otherwise
		 */

	public boolean
	activationRequested(
		DownloadActivationEvent	event );
}
