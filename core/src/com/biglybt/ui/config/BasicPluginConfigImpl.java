/*
 * Created on 28-Apr-2004
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

package com.biglybt.ui.config;

import java.lang.ref.WeakReference;

import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

public class
BasicPluginConfigImpl
	extends ConfigSectionImpl {
	private WeakReference<BasicPluginConfigModel>		model_ref;

	public
	BasicPluginConfigImpl(
		WeakReference<BasicPluginConfigModel>	_model_ref )
	{
		super(_model_ref);

		model_ref			= _model_ref;
	}

	@Override
	public void deleteConfigSection() {
		// Prevent Parameters created via BasicPluginConfigModel from being destroyed
		// They are only created once and used multiple times.
		// Must be before super.
		mapPluginParams.clear();
		
		super.deleteConfigSection();
	}

	@Override
	public void build() {
		// Normally, this is where we would build the map of Parameter objects
		// But, Plugins come with the Parameters already created

		BasicPluginConfigModel	model = model_ref.get();
		if (model == null) {
			return;
		}
		Parameter[] parameters = model.getParameters();
		for (Parameter parameter : parameters) {
			// assumed all Parameters are ParameterImpl
			if (parameter instanceof ParameterImpl) {
				add((ParameterImpl) parameter);
			}
		}
	}

}
