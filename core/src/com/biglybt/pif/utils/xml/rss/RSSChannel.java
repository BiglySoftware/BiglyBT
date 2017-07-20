/*
 * Created on 02-Jan-2005
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

package com.biglybt.pif.utils.xml.rss;

import java.net.URL;
import java.util.Date;

import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

/**
 * @author parg
 *
 */

public interface
RSSChannel
{
		/**
		 * Returns the mandatory title
		 * @return
		 */

	public String
	getTitle();

		/**
		 * Returns the mandatory description
		 * @return
		 */

	public String
	getDescription();

		/**
		 * Returns the mandatory link
		 * @return
		 */

	public URL
	getLink();

		/**
		 * Returns the *optional* publication date
		 * @return
		 */

	public Date
	getPublicationDate();

		/**
		 * returns the items in the channel
		 * @return
		 */

	public RSSItem[]
	getItems();

		/**
		 * returns the underlying XML node for extraction of extensions
		 * @return
		 */

	public SimpleXMLParserDocumentNode
	getNode();
}
