/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
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
 */

package com.biglybt.core.metasearch.impl;

import java.util.regex.Matcher;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;

public class FieldRemapper {

	private int inField;
	private int outField;

	private FieldRemapping[] fieldRemappings;


	public
	FieldRemapper(
		int 				inField,
		int 				outField,
		FieldRemapping[] 	fieldRemappings)
	{
		super();
		this.inField = inField;
		this.outField = outField;
		this.fieldRemappings = fieldRemappings;
	}

	public int
	getInField()
	{
		return( inField );
	}

	public int
	getOutField()
	{
		return( outField );
	}

	public FieldRemapping[]
	getMappings()
	{
		return( fieldRemappings );
	}

	public void
	remap(
		Result r )
	{
		String input = null;
		switch(inField) {
			case Engine.FIELD_CATEGORY :
				input = r.getCategory();
				break;
		}

		String output = null;
		if(input != null) {
			for(int i = 0 ; i < fieldRemappings.length ; i++) {
				if(fieldRemappings[i].getMatch() != null && fieldRemappings[i].getReplacement() != null) {
					Matcher matcher = fieldRemappings[i].getMatch().matcher(input);
					if(matcher.matches()) {
						output = matcher.replaceAll(fieldRemappings[i].getReplacement());
						i = fieldRemappings.length;
					}
				}
			}
		}

		if(output != null) {
			switch(outField) {
			case Engine.FIELD_CATEGORY :
				r.setCategory(output);
				break;
			case Engine.FIELD_CONTENT_TYPE :
				r.setContentType(output);
				break;
			}
		}

	}

}
