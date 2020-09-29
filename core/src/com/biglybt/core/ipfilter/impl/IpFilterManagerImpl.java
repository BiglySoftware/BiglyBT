/*
 * Created on 19-Jul-2004
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

package com.biglybt.core.ipfilter.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.ipfilter.BadIps;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManager;
import com.biglybt.core.ipfilter.IpRange;
import com.biglybt.core.util.FileUtil;

public class
IpFilterManagerImpl
	implements IpFilterManager, ParameterListener
{
	protected static final IpFilterManagerImpl		singleton	= new IpFilterManagerImpl();

	private Object lock = new Object();
	
	private RandomAccessFile rafDescriptions = null;

	/**
	 *
	 */
	public IpFilterManagerImpl() {
		COConfigurationManager.addAndFireParameterListener(
				"Ip Filter Enable Description Cache", this);
	}

	@Override
	public Object 
	addDescription(
		IpRange range, byte[] description) 
	{
		synchronized( lock ){
			if (rafDescriptions == null) {
				return null;
			}
	
			try {
				if (description == null || description.length == 0)
					return null;
	
				int start;
				int end;
				start = (int)rafDescriptions.getFilePointer();
				int len = (int)rafDescriptions.length();
	
				//System.out.println(len - 0x1FFFFFF);
				if (len + 61 >= 0x1FFFFFF) {
					// we could try to fit a desc < 61, but why bother.. at this point
					// we have at least 550,072 ranges
					return null;
				}
	
				if (start != len) {
					rafDescriptions.seek(len);
					start = (int)rafDescriptions.getFilePointer();
				}
	
				// last 25: position
				// 26 - 31 (6, 61 chars max): len
	
				if (description.length <= 61) {
					rafDescriptions.write(description);
				} else {
					rafDescriptions.write(description, 0,  61);
				}
				end = (int)rafDescriptions.getFilePointer();
	
				//System.out.println("add " + new String(description, 0, (end - start)) + "; " + start + " - " + end);
	
				int info = start + ((end - start) << 25);
	
				return new Integer(info);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	
			return null;
		}
	}

	@Override
	public byte[] getDescription(Object info) {
		// if cached, info is an object array, with the first index being the descr
		if (info instanceof Object[]) {
			return (byte[])(((Object[])info)[0]);
		}

		synchronized( lock ){
			if (rafDescriptions == null || !(info instanceof Integer)) {
				return "".getBytes();
			}
	
			try {
				int posInfo = ((Integer)info).intValue();
				int pos = posInfo & 0x1FFFFFF;
				int len = posInfo >> 25;
	
				if (len < 0) {
					throw new IllegalArgumentException(getClass().getName() + ": invalid posInfo [" + posInfo +"], pos [" + pos + "], len [" + len + "]");
				}
	
				if (rafDescriptions.getFilePointer() != pos) {
					rafDescriptions.seek(pos);
				}
	
				byte[] bytes = new byte[len];
				rafDescriptions.read(bytes);
	
				return bytes;
			} catch (IOException e) {
				return "".getBytes();
			}
		}
	}

	@Override
	public void cacheAllDescriptions() {
		IpRange[] ranges = getIPFilter().getRanges();
		for (int i = 0; i < ranges.length; i++) {
			Object info = ((IpRangeImpl)ranges[i]).getDescRef();
			if (info instanceof Integer) {
				byte[] desc = getDescription(info);
				Object[] data = { desc, info };
				((IpRangeImpl)ranges[i]).setDescRef(data);
			}
		}
	}

	@Override
	public void clearDescriptionCache() {
		IpRange[] ranges = getIPFilter().getRanges();
		for (int i = 0; i < ranges.length; i++) {
			Object info = ((IpRangeImpl)ranges[i]).getDescRef();
			if (info instanceof Object[]) {
				Integer data = (Integer)((Object[])info)[1];
				((IpRangeImpl)ranges[i]).setDescRef(data);
			}
		}
	}

	@Override
	public void 
	deleteAllDescriptions() 
	{
		synchronized( lock ){
			if (rafDescriptions != null) {
	  		try {
	  			rafDescriptions.close();
	  		} catch (IOException e) {
	  		}
	  		rafDescriptions = null;
			}
		}
		
		parameterChanged(null);
	}

	public static IpFilterManager
	getSingleton()
	{
		return( singleton );
	}

	@Override
	public IpFilter
	getIPFilter()
	{
		return( IpFilterImpl.getInstance());
	}

	@Override
	public BadIps
	getBadIps()
	{
		return (BadIpsImpl.getInstance());
	}

	@Override
	public void 
	parameterChanged(
		String parameterName) 
	{
		boolean enable = COConfigurationManager.getBooleanParameter("Ip Filter Enable Description Cache");
		
		synchronized( lock ){
			
			if (enable && rafDescriptions == null) {
				File fDescriptions = FileUtil.getUserFile("ipfilter.cache");
				try {
					if (fDescriptions.exists()) {
						fDescriptions.delete();
					}
					rafDescriptions = new RandomAccessFile(fDescriptions, "rw");
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (!enable && rafDescriptions != null) {
				try {
					rafDescriptions.close();
				} catch (IOException e) {
				}
				rafDescriptions = null;
			}
		}
	}
}
