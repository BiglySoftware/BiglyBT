/*
 * Created on 22-Apr-2005
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

package com.biglybt.core.util;

import java.io.PrintWriter;

public class
IndentWriter
{
	private static final String	INDENT_STRING		= "    ";
	private static final String	INDENT_STRING_HTML	= "&nbsp;&nbsp;&nbsp;&nbsp;";

	private final PrintWriter		pw;
	private String			indent	= "";

	private boolean			html;

	private boolean			force;

	public
	IndentWriter(
		PrintWriter	_pw )
	{
		pw	= _pw;
	}

	public void
	setHTML(
		boolean	_html )
	{
		html = _html;
	}

	public void
	println(
		String	str )
	{
		if ( html ){

			pw.print( indent + str + "<br>" );

		}else{

			pw.println( indent + str );
		}

		if ( force ){

			pw.flush();
		}
	}

	public void
	indent()
	{
		indent += html?INDENT_STRING_HTML:INDENT_STRING;
	}

	public void
	exdent()
	{
		if ( indent.length() > 0 ){

			indent = indent.substring((html?INDENT_STRING_HTML:INDENT_STRING).length());
		}
	}

	public String
	getTab()
	{
		return( html?INDENT_STRING_HTML:INDENT_STRING );
	}

	public void
	setForce(
		boolean	b )
	{
		force	= b;
	}

	public void
	close()
	{
		pw.close();
	}
}
