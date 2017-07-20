/*
 * Created on 27-Apr-2004
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

package com.biglybt.pifimpl.local.ui.components;

/**
 * @author parg
 *
 */

import com.biglybt.pif.ui.components.UIProgressBar;

public class
UIProgressBarImpl
	extends		UIComponentImpl
	implements 	UIProgressBar
{
	public
	UIProgressBarImpl()
	{
		setPercentageComplete(0);
	}

	@Override
	public void
	setPercentageComplete(
		int		percentage )
	{
		setProperty( PT_VALUE, new Integer(percentage));
	}

	@Override
	public int
	getPercentageComplete()
	{
		return(((Integer)getProperty( PT_VALUE )).intValue());
	}
}
