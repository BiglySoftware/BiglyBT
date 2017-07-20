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

package com.biglybt.core.html.impl;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;


public class
HTMLChunkImpl
{
	String		content;

	protected
	HTMLChunkImpl()
	{
	}

	protected
	HTMLChunkImpl(
		String		_content )
	{
		content	= _content;
	}

	protected void
	setContent(
		String		str )
	{
		content	= str;
	}

		/**
		 * this just returns the tags themselves.
		 * @param tag
		 * @return
		 */

	protected String[]
	getTags(
		String	tag_name )
	{
		tag_name = tag_name.toLowerCase( MessageText.LOCALE_ENGLISH );

		String	lc_content = content.toLowerCase( MessageText.LOCALE_ENGLISH );

		int	pos	= 0;

		List	res = new ArrayList();

		while(true){

			int	p1 = lc_content.indexOf( "<" + tag_name,  pos );

			if ( p1 == -1 ){

				break;
			}

			int	p2 = lc_content.indexOf( ">", p1 );

			if ( p2 == -1 ){

				break;
			}

			res.add( content.substring( p1+1, p2 ));

			pos	= p2+1;
		}

		String[]	x = new String[res.size()];

		res.toArray( x );

		return( x );
	}

	public String
	getContent()
	{
		return( content );
	}
}
