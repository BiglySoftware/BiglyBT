/*
 * Created on 12 May 2008
 * Created by Allan Crooks
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
package com.biglybt.core.download;

import com.biglybt.pif.download.DownloadAttributeListener;

/**
 * @author Allan Crooks
 *
 */
public interface DownloadManagerStateAttributeListener {
	public static final int	WRITTEN = DownloadAttributeListener.WRITTEN;
	public static final int	WILL_BE_READ = DownloadAttributeListener.WILL_BE_READ;
	public void attributeEventOccurred(DownloadManager download, String attribute, int event_type);
}
