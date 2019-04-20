/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.pif.ui.config;

/**
 * Parameter representing a link to be clicked.  Can be URI or file
 * 
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addHyperlinkParameter2(String, String)
 */
public interface HyperlinkParameter
	extends Parameter
{
	/**
	 * @since BiglyBT 1.0.0.0
	 */
	void setHyperlink(String url_location);

	/**
	 * @since BiglyBT 1.0.0.0
	 */
	String getHyperlink();

	/**
	 * The messagebundle key for the link text
	 * 
	 * @since BiglyBT 1.9.0.1
	 */
	String getLinkTextKey();
}
