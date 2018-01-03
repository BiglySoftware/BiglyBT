/*
 * Created on 17-Nov-2004
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

package com.biglybt.core.update;

/**
 * @author parg
 */

import com.biglybt.core.Core;

import java.lang.reflect.Constructor;

public class
ClientRestarterFactory {

	public static final String DEFAULT_FACTORY = "com.biglybt.core.update.impl.ClientRestarterImpl";

	public static ClientRestarter
	create(
			Core core) {
		String className = System.getProperty("az.factory.ClientRestarter.impl", DEFAULT_FACTORY);
		if (className != null) {
			try {
				final Class<?> cla = Class.forName(className);
				final Constructor<?> constructor = cla.getConstructor(Core.class);
				return (ClientRestarter) constructor.newInstance(core);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		return null;
	}
}
