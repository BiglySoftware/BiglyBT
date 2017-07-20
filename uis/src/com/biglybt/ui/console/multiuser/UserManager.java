/*
 * Created on 25/01/2005
 * Created by Paul Duran
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.console.multiuser;

import java.io.*;
import java.util.*;

import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.console.UserProfile;

import com.biglybt.core.CoreException;

/**
 * The usermanager is responsible for reading the users configuration
 * file and loading in all of the possible users. It is also responsible
 * for authenticating a username/password
 * @author pauld
 */
public class UserManager
{
	private static final String USER_DB_CONFIG_FILE = "console.users.properties";

	private static UserManager instance;
	private Map usersMap = new HashMap();

	private final String fileName;

	/**
	 * @param configFile
	 */
	public UserManager(String fileName)
	{
		super();
		this.fileName = fileName;
	}

	/**
	 * attempts to locate a user with the specified username and then
	 * verifies that the specified password is the same as the password
	 * associated with that user
	 * @param username
	 * @param password
	 * @return
	 */
	public UserProfile authenticate( String username, String password )
	{
		UserProfile profile = getUser(username);
		if( profile != null)
		{
			if( profile.authenticate( password ) )
				return profile;
		}
		return null;
	}

	/**
	 * returns the profile for the user with the specified username
	 * otherwise null if there is no such user
	 * @param username
	 * @return
	 */
	public UserProfile getUser(String username )
	{
		return (UserProfile) usersMap.get(username.toLowerCase());
	}

	/**
	 * adds another user to the users list
	 * @param user
	 */
	public void addUser(UserProfile user)
	{
		usersMap.put( user.getUsername().toLowerCase(), user );
	}

	public Collection getUsers( )
	{
		return Collections.unmodifiableCollection(usersMap.values());
	}

	/**
	 * load a new UserManager object from the specified input stream.
	 * The input stream should contain an XML document as encoded by the
	 * save() method
	 * @param in
	 * @return UserManager object
	 * @throws FileNotFoundException
	 */
	public void load( ) throws FileNotFoundException
	{
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileName));
		doLoad( bis );
	}

	protected void doLoad( InputStream in )
	{
		try{
			Class cla = Class.forName("com.biglybt.ui.console.multiuser.persist.UserManagerXMLPersist");

			UserManagerPersister persister = (UserManagerPersister)cla.newInstance();

			persister.doLoad( in, usersMap );

		}catch( ClassNotFoundException e ){

			System.err.println( "No persistence service for user config, load not performed" );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	/**
	 * write the UserManager configuration out to the specified output stream.
	 * the configuration is stored in XML format as specified by the XMLEncoder class
	 * @param out
	 * @throws FileNotFoundException
	 * @see XMLEncoder
	 */
	public void save( ) throws FileNotFoundException
	{
		OutputStream out = new FileOutputStream(fileName);
		doSave(out);
	}

	protected void doSave( OutputStream out )
	{
		try{
			Class cla = Class.forName("com.biglybt.ui.console.multiuser.persist.UserManagerXMLPersist");

			UserManagerPersister persister = (UserManagerPersister)cla.newInstance();

			persister.doSave( out, usersMap );

		}catch( ClassNotFoundException e ){

			System.err.println( "No persistence service for user config, save not performed" );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	public static UserManager getInstance(PluginInterface pi)
	{
		synchronized( UserManager.class ){
			if( instance == null )
			{
				String userDir = pi.getUtilities().getUserDir();
				File dbFile = new File(userDir, USER_DB_CONFIG_FILE);

				try {
					instance = new UserManager(dbFile.getCanonicalPath());
					if( dbFile.exists() )
					{
						System.out.println("loading user configuration from: " + dbFile.getCanonicalPath());
						instance.load();
					}
					else
					{
						System.out.println("file: " + dbFile.getCanonicalPath() + " does not exist. using 'null' user manager");
					}
				} catch (IOException e)
				{
					throw new CoreException("Unable to instantiate default user manager");
				}

			}
			return instance;
		}
	}

	public static final class UserManagerConfig
	{
		private List users = new ArrayList();

		/**
		 * @return Returns the users.
		 */
		public List getUsers() {
			return users;
		}

		/**
		 * @param users The users to set.
		 */
		public void setUsers(List users) {
			this.users = users;
		}

		/**
		 * adds another user to the users list
		 * @param user
		 */
		public void addUser(UserProfile user)
		{
			users.add( user );
		}

		public void clear()
		{
			users.clear();
		}
	}

	/**
	 * removes the user with the specified name
	 * @param userName
	 */
	public void deleteUser(String userName)
	{
		usersMap.remove(userName.toLowerCase());
	}
}
