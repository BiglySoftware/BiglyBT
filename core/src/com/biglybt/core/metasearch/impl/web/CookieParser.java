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

package com.biglybt.core.metasearch.impl.web;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class CookieParser {

	public static boolean cookiesContain(String[] requiredCookies,String cookies) {
		if(cookies == null) return false;
		boolean[] cookieFound = new boolean[requiredCookies.length];

		String[] names = getCookiesNames(cookies);

		for(int j = 0 ; j < names.length ; j++) {
			String cookieName = names[j];
			for(int i = 0 ; i < requiredCookies.length ;i++) {
				if(requiredCookies[i].equals(cookieName)) {
					cookieFound[i] = true;
				}
			}
		}

		for(int i = 0 ; i < cookieFound.length ; i++) {
			if(!cookieFound[i]) return false;
		}

		return true;
	}

	public static String[] getCookiesNames(String cookies) {
		if(cookies == null) return new String[0];

		StringTokenizer st = new StringTokenizer(cookies,"; ");
		List names = new ArrayList();

		while(st.hasMoreTokens()) {
			String cookie = st.nextToken();
			int separator = cookie.indexOf("=");
			if(separator > -1) {
				names.add(cookie.substring(0,separator));
			}
		}

		String[] result = (String[]) names.toArray(new String[names.size()]);

		return result;

	}

}
