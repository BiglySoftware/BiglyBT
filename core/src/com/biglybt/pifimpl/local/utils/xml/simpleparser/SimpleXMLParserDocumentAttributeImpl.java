/*
 * File    : SimpleXMLParserDocumentAttributeImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.utils.xml.simpleparser;

import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;

public class
SimpleXMLParserDocumentAttributeImpl
	implements SimpleXMLParserDocumentAttribute
{
	protected String		name;
	protected String		value;

	protected
	SimpleXMLParserDocumentAttributeImpl(
		String		_name,
		String		_value )
	{
		name		= _name;
		value		= _value;
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String
	getValue()
	{
		return( value );
	}
}
