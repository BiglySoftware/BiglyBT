/*
 * Created on 10-Jan-2005
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

package com.biglybt.pifimpl.local.ui.config;

import java.util.List;

import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterGroup;

/**
 * @author parg
 *
 */

public class
ParameterGroupImpl
	extends ParameterImpl
	implements ParameterGroup
{
	private String				resource;
	private ParameterImpl[]		parameters;

	private int		num_columns = 1;

	private ParameterTabFolderImpl		tab_folder;

	public
	ParameterGroupImpl(
			String			_resource,
			List<? extends  Parameter> _parameters )
	{
		this(_resource, _parameters.toArray(new Parameter[0]));
	}

	public
	ParameterGroupImpl(
			String			_resource,
		Parameter...		_parameters )
	{
		super(null, "" );

		resource = _resource;

		if ( _parameters != null ){

			parameters = new ParameterImpl[_parameters.length];

			for (int i=0;i<_parameters.length;i++){

				ParameterImpl parameter = (ParameterImpl)_parameters[i];

				parameters[i] = parameter;

				if ( parameter != null ){

					parameter.setGroup( this );
				}
			}
		}
	}

	public void
	setTabFolder(
		ParameterTabFolderImpl		tf )
	{
		tab_folder	= tf;
	}

	public ParameterTabFolderImpl
	getTabFolder()
	{
		return( tab_folder );
	}

	public String
	getResourceName()
	{
		return( resource );
	}


	public ParameterGroupImpl
	setNumberOfColumns2(
			int		num )
	{
		num_columns		= num;
		return this;
	}

	@Override
	public void
	setNumberOfColumns(
		int		num )
	{
		num_columns		= num;
	}

	public int
	getNumberColumns()
	{
		return( num_columns );
	}

	@Override
	public void
	setEnabled(
		boolean	e )
	{
		super.setEnabled( e );
		
		for ( ParameterImpl p: parameters ){
			
			p.setEnabled( e );
		}
	}
	
	public ParameterImpl[]
	getParameters()
	{
		return( parameters );
	}
	
	public int size(boolean countChildren) {
		if (!countChildren) {
			return parameters.length;
		}
		int count = parameters.length;
		for (ParameterImpl param : parameters) {
			if (param instanceof ParameterGroupImpl) {
				count += ((ParameterGroupImpl) param).size(true);
			}
		}
		return count;
	}

	@Override
	public Object getValueObject() {
		return null;
	}
}

