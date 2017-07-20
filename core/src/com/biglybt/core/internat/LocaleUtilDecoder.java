/*
 * Created on 21-Jun-2004
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


package com.biglybt.core.internat;

/**
 * @author parg
 *
 */

import java.io.UnsupportedEncodingException;

public interface
LocaleUtilDecoder
{
	public String
	getName();

	public int
	getIndex();

	public String
	tryDecode(
		byte[]		array,
		boolean		lax );

	public String
	decodeString(
		byte[]		bytes )

		throws UnsupportedEncodingException;
}
