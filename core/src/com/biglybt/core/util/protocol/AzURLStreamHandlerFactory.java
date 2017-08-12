/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.core.util.protocol;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * @author Aaron Grunthal
 * @create 30.03.2008
 */
public class AzURLStreamHandlerFactory implements URLStreamHandlerFactory {
	private static final String	packageName	= AzURLStreamHandlerFactory.class.getPackage().getName();
	private static AzURLStreamHandlerFactory instance = null;
	
	public AzURLStreamHandlerFactory() {
		instance = this;
	}

	public static AzURLStreamHandlerFactory getInstance() {
		return instance;
	}

	@Override
	public URLStreamHandler createURLStreamHandler(String protocol) {
		// don't do classloading when called for protocols that are involved in classloading
		if(protocol.equals("file") || protocol.equals("jar"))
			return null;
		String clsName = packageName + "." + protocol + ".Handler";
		try
		{
			Class cls = Class.forName(clsName);
			return (URLStreamHandler) cls.newInstance();
		} catch (Throwable e)
		{
			// URLs are involved in classloading, evil things might happen
		}
		return null;
	}
}
