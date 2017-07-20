/*
 * Created on Sep 17, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.browser.listener;

import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.ui.selectedcontent.DownloadUrlInfo;

/**
 * @author TuxPaper
 * @created Sep 17, 2008
 *
 */
public class DownloadUrlInfoSWT
	extends DownloadUrlInfo
{

	private final ClientMessageContext context;

	private final String callback;

	private final String hash;

	/**
	 * @param hash
	 * @param url
	 */
	public DownloadUrlInfoSWT(ClientMessageContext context, String callback,
			String hash) {
		super(null);
		this.context = context;
		this.callback = callback;
		this.hash = hash;
	}

	/**
	 * @return the context
	 */
	public ClientMessageContext getContext() {
		return context;
	}

	/**
	 * @return the callback
	 */
	public String getCallback() {
		return callback;
	}

	public void invoke(String reason) {
		context.executeInBrowser(callback + "('" + reason + "','" + hash + "','"
				+ getDownloadURL() + "')");
	}
}
