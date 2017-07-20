/*
 * Created on 30-Aug-2004
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

package com.biglybt.pifimpl.local.config;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.ConfigParameterListener;

public class
ConfigParameterImpl
	implements ConfigParameter, ParameterListener
{
	protected String			key;

	protected List				listeners	= new ArrayList();

	public
	ConfigParameterImpl(
		String						_key )
	{
		key		= _key;
	}

	@Override
	public void
	parameterChanged(
		String parameterName)
	{
		for ( int i=0;i<listeners.size();i++){

			((ConfigParameterListener)listeners.get(i)).configParameterChanged( this );
		}
	}

	@Override
	public void
	addConfigParameterListener(
		ConfigParameterListener	l )
	{
		listeners.add(l);

		if ( listeners.size() == 1 ){

			COConfigurationManager.addParameterListener( key, this );
		}
	}

	@Override
	public void
	removeConfigParameterListener(
		ConfigParameterListener	l )
	{
		listeners.remove(l);

		if ( listeners.size() == 0 ){

			COConfigurationManager.removeParameterListener( key, this );
		}
	}
}
