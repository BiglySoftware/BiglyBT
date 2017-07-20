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

package com.biglybt.ui.console;

import com.biglybt.ui.console.util.StringEncrypter;
import com.biglybt.ui.console.util.StringEncrypter.EncryptionException;

import com.biglybt.core.CoreException;


/**
 * the user profile contains the information about a user that is allowed to use the console ui.
 * Users may be assigned one of three profiles:<br>
 * <ul>
 * <li><b>ADMIN</b> have full access to all commands and to torrents of all users
 * <li><b>USER</b> have limited access to commands - can only add/modify their own torrents
 * <li><b>GUEST</b> have no access - can only view the torrent download status
 * </ul>
 * @author pauld
 */
public class UserProfile
{
	private String username;
	private String userType;
	private String encryptedPassword;
	private String defaultSaveDirectory;

	public static final String ADMIN = "admin";
	public static final String USER = "user";
	public static final String GUEST = "guest";
	public static final String DEFAULT_USER_TYPE = ADMIN;

	public static final UserProfile DEFAULT_USER_PROFILE = new UserProfile("admin", ADMIN);

	/**
	 * returns true if the specified value is a valid user type
	 * @param userType
	 * @return
	 */
	public static boolean isValidUserType( String userType )
	{
		return ADMIN.equals(userType) || USER.equals(userType) || GUEST.equals(userType);
	}

	/**
	 *
	 */
	public UserProfile() {
		super();
		this.userType = DEFAULT_USER_TYPE;
	}

	public UserProfile(String name, String userType)
	{
		this.username = name;
		setUserType(userType);
	}

	/**
	 * returns true if the specified password is the password for this
	 * user profile
	 * @param password
	 * @return
	 */
	public boolean authenticate(String password) {
		StringEncrypter encrypter;
		try {
			encrypter = new StringEncrypter(StringEncrypter.DES_ENCRYPTION_SCHEME);
			return encrypter.decrypt(encryptedPassword).equals(password);
		} catch (EncryptionException e) {
			throw new CoreException("Unable to decrypt password", e);
		}
	}

	/**
	 * stores the specified password as an encrypted password
	 * @param password The password to set.
	 */
	public void setPassword(String password)
	{
		try {
			StringEncrypter encrypter = new StringEncrypter(StringEncrypter.DES_ENCRYPTION_SCHEME);
			setEncryptedPassword(encrypter.encrypt(password));
		} catch (EncryptionException e)
		{
			throw new CoreException("Unable to encrypt password", e);
		}
	}


	/**
	 * @return Returns the username.
	 */
	public String getUsername() {
		return username;
	}
	/**
	 * @param username The username to set.
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	/**
	 * @return Returns the userType.
	 */
	public String getUserType() {
		return userType;
	}
	/**
	 * @param userType The userType to set.
	 */
	public void setUserType(String userType) {
		if(userType.equalsIgnoreCase(ADMIN))
			userType = ADMIN;
		else if(userType.equalsIgnoreCase(USER))
			userType = USER;
		else if(userType.equalsIgnoreCase(GUEST))
			userType = GUEST;
		else
			userType = DEFAULT_USER_TYPE;
		this.userType = userType;
	}

	/**
	 * check for equality with another user profile object
	 */
	public boolean equals(Object obj) {
		if( obj == null || ! (obj instanceof UserProfile) )
			return false;
		UserProfile other = (UserProfile)obj;
		if( getUsername() != null )
			return getUsername().equals(other.getUsername());
		else
			if( other.getUsername() != null )
				return false;
		if( getEncryptedPassword() != null )
			return getEncryptedPassword().equals(other.getEncryptedPassword());
		else
			if( other.getEncryptedPassword() != null )
				return false;

		return true;
	}
	/**
	 * @return Returns the encryptedPassword.
	 */
	public String getEncryptedPassword() {
		return encryptedPassword;
	}
	/**
	 * @param encryptedPassword The encryptedPassword to set.
	 */
	public void setEncryptedPassword(String encryptedPassword) {
		this.encryptedPassword = encryptedPassword;
	}

	/**
	 * @return the directory that torrents should be saved to for this user, by default
	 */
	public String getDefaultSaveDirectory() {
		return defaultSaveDirectory;
	}

	public void setDefaultSaveDirectory(String newValue) {
		this.defaultSaveDirectory = newValue;
	}
}
