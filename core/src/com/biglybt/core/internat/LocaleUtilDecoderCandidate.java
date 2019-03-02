/*
 * Created on 25-Jul-2004
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


public class
LocaleUtilDecoderCandidate
	implements Comparable
{
	private final int					index;
	private String 				value;
	private LocaleUtilDecoder	decoder;

	protected
	LocaleUtilDecoderCandidate(
		int	_index )
	{
		index	= _index;
	}

	public String getValue() {
	  return value;
	}

	public LocaleUtilDecoder getDecoder() {
	  return decoder;
	}

	public void
	setDetails(
		LocaleUtilDecoder	_decoder,
		String				_value )
	{
		decoder	= _decoder;
		value	= _value;
	}

	@Override
	public int
	compareTo(Object o)
	{
	  LocaleUtilDecoderCandidate candidate = (LocaleUtilDecoderCandidate)o;

	  int	res;

	  if( value == null && candidate.value == null){

		res	= 0;

	  }else if ( value == null ){

		res = 1;

	  }else if ( candidate.value == null ){

		res = -1;

	  }else{

		res = value.length() - candidate.value.length();

		if ( res == 0 ){

			res = index - candidate.index;
		}
	  }

	  if ( decoder != null && candidate.getDecoder() != null ){

	  	// System.out.println( "comp:" + decoder.getName() + "/" + candidate.getDecoder().getName() + " -> " + res );
	  }
	  return( res );
	}

	public int getIndex() {
		return index;
	}
}