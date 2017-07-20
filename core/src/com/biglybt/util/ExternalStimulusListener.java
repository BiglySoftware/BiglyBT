/*
 * Created on Feb 8, 2007
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


package com.biglybt.util;

import java.util.Map;

public interface
ExternalStimulusListener
{
		/**
		 *
		 * @param name
		 * @param values
		 * @return false if not handled
		 */

	public boolean
	receive(
		String		name,
		Map			values );

		/**
		 *
		 * @param name
		 * @param values
		 * @return Integer.MIN_VALUE if not handled
		 */

	public int
	query(
		String		name,
		Map			values );
}
