/*
 * Created on May 23, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.ui.console.multiuser.persist;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.console.UserProfile;
import com.biglybt.ui.console.multiuser.UserManagerPersister;
import com.biglybt.ui.console.multiuser.UserManager.UserManagerConfig;

public class
UserManagerXMLPersist
	implements UserManagerPersister
{
		/**
		 * This code abstracted because it relies on java.beans.* which is not always available
		 * e.g. on Android
		 */

	@Override
	public void
	doSave(
		OutputStream 	out,
		Map				usersMap )
	{
		UserManagerConfig config = new UserManagerConfig();
		List users = new ArrayList( usersMap.values() );
		config.setUsers(users);

		XMLEncoder encoder = new XMLEncoder( new BufferedOutputStream( out ) );
		encoder.writeObject(config);
		encoder.close();
	}

	@Override
	public void
	doLoad(
		InputStream 	in,
		Map				usersMap )
	{
		XMLDecoder decoder = new XMLDecoder( in );
		UserManagerConfig managerConfig = (UserManagerConfig)decoder.readObject();
		for (Iterator iter = managerConfig.getUsers().iterator(); iter.hasNext();) {
			UserProfile user = (UserProfile) iter.next();
			usersMap.put(user.getUsername().toLowerCase(), user);
		}
		System.out.println("UserManager: registered " + usersMap.size() + " users");
		decoder.close();
	}
}
