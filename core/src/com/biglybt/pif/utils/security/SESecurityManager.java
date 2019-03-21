/*
 * Created on 17-Jun-2004
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

package com.biglybt.pif.utils.security;

/**
 * @author parg
 *
 */

import java.net.Authenticator;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;

import javax.net.ssl.SSLSocketFactory;

import com.biglybt.pif.messaging.generic.GenericMessageConnection;

public interface
SESecurityManager
{
	public static final int	BLOCK_ENCRYPTION_NONE		= 1;
	public static final int	BLOCK_ENCRYPTION_AES		= 2;

		// runs the given task with the supplied Authenticator. Note that the
		// scope of the authenticator is "vm-wide" so that if by chance another
		// thread attempts to perform an operation that requires authentication
		// which the supplied one is in force, the request will be directed to the
		// authenticator

	public void
	runWithAuthenticator(
		Authenticator	authenticator,
		Runnable		task );

	public void
	addPasswordListener(
		PasswordListener	listener );

	public void
	removePasswordListener(
		PasswordListener	listener );

	public void
	addCertificateListener(
		CertificateListener	listener );

	public void
	removeCertificateListener(
		CertificateListener	listener );

		/**
		 * returns the SHA1 hash of the input data
		 * @param data_in
		 * @return
		 */

	public byte[]
	calculateSHA1(
		byte[]		data_in );

		/**
		 * Installs the SSL certificate necessary to support the connection
		 * @param url
		 */

	public SSLSocketFactory
	installServerCertificate(
		URL		url );

	public KeyStore
	getKeyStore()

		throws Exception;

	public KeyStore
	getTrustStore()

		throws Exception;

		/**
		 * creates and installs a certificate capable of supporting SSL of type MD5withRSA
		 * @param alias		alias - e.g. "mycert"
		 * @param cert_dn	dn for the cert  e.g. "CN=fred,OU=wap,O=wip,L=here,ST=there,C=GB"
		 * @param strength	keyt strength - e.g. 1024
		 * @return
		 * @throws Exception
		 */

	public Certificate
	createSelfSignedCertificate(
		String		alias,
		String		cert_dn,
		int			strength )

		throws Exception;

		/**
		 * Gets this the client instance's unique random identity
		 * @return
		 */

	public byte[]
	getIdentity();

		/**
		 * Gets the public key for this az instance of the supplied key type
		 * @param key_type	see KEY_TYPE_x constants in SEPublicKey
		 * @param reason_resource a message text resource giving the reason for the key being required
		 * @return
		 */

	public SEPublicKey
	getPublicKey(
		int		key_type,
		String	reason_resource )

		throws Exception;

	public SEPublicKey
	getPublicKey(
		int		key_type,
		int		instance,
		String	reason_resource )

		throws Exception;


		/**
		 * Returns a proxy generic STS connection for incoming connection requests
		 * @param connection
		 * @param my_public_key
		 * @param key_locator
		 * @return
		 * @throws Exception
		 */

	public GenericMessageConnection
	getSTSConnection(
		GenericMessageConnection	connection,
		SEPublicKey					my_public_key,
		SEPublicKeyLocator			key_locator,
		String						reason_resource,
		int							block_encryption )

		throws Exception;
}
