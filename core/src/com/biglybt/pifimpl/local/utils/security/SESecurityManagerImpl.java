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

package com.biglybt.pifimpl.local.utils.security;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import com.biglybt.core.Core;
import com.biglybt.core.security.SECertificateListener;
import com.biglybt.core.security.SEPasswordListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.SHA1Hasher;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.utils.security.CertificateListener;
import com.biglybt.pif.utils.security.PasswordListener;
import com.biglybt.pif.utils.security.SEPublicKey;
import com.biglybt.pif.utils.security.SEPublicKeyLocator;
import com.biglybt.pifimpl.local.messaging.GenericMessageConnectionImpl;

/**
 * @author parg
 */


public class
SESecurityManagerImpl
	implements com.biglybt.pif.utils.security.SESecurityManager
{
	private Core core;

	private Map	password_listeners		= new HashMap();
	private Map	certificate_listeners	= new HashMap();

	public
	SESecurityManagerImpl(
		Core _core )
	{
		core	= _core;
	}

	@Override
	public byte[]
	calculateSHA1(
		byte[]		data_in )
	{
		if (data_in == null ){

			data_in = new byte[0];
		}

        SHA1Hasher hasher = new SHA1Hasher();

        return( hasher.calculateHash(data_in));
	}

	@Override
	public void
	runWithAuthenticator(
		Authenticator	authenticator,
		Runnable		target )
	{
		try{
			Authenticator.setDefault( authenticator );

			target.run();

		}finally{

			SESecurityManager.installAuthenticator();
		}
	}

	@Override
	public void
	addPasswordListener(
		final PasswordListener	listener )
	{
		SEPasswordListener	sepl =
			new SEPasswordListener()
			{
				@Override
				public PasswordAuthentication
				getAuthentication(
					String		realm,
					URL			tracker )
				{
					return( listener.getAuthentication( realm, tracker ));
				}

				@Override
				public void
				setAuthenticationOutcome(
					String		realm,
					URL			tracker,
					boolean		success )
				{
					listener.setAuthenticationOutcome( realm, tracker, success );
				}

				@Override
				public void
				clearPasswords()
				{
				}
			};

		password_listeners.put( listener, sepl );

		SESecurityManager.addPasswordListener( sepl );
	}

	@Override
	public void
	removePasswordListener(
		PasswordListener	listener )
	{
		SEPasswordListener	sepl = (SEPasswordListener)password_listeners.get( listener );

		if ( sepl != null ){

			SESecurityManager.removePasswordListener( sepl );
		}
	}

	@Override
	public void
	addCertificateListener(
		final CertificateListener	listener )
	{
		SECertificateListener	sepl =
			new SECertificateListener()
			{
			@Override
			public boolean
			trustCertificate(
				String			resource,
				X509Certificate	cert )
			{
				return( listener.trustCertificate( resource, cert ));
			}
			};

		certificate_listeners.put( listener, sepl );

		SESecurityManager.addCertificateListener( sepl );
	}

	@Override
	public void
	removeCertificateListener(
			CertificateListener	listener )
	{
		SECertificateListener	sepl = (SECertificateListener)certificate_listeners.get( listener );

		if ( sepl != null ){

			SESecurityManager.removeCertificateListener( sepl );
		}
	}

	@Override
	public SSLSocketFactory
	installServerCertificate(
		URL		url )
	{
		return( SESecurityManager.installServerCertificates( url ));
	}

	@Override
	public KeyStore
	getKeyStore()

		throws Exception
	{
		return( SESecurityManager.getKeyStore());
	}

	@Override
	public KeyStore
	getTrustStore()

		throws Exception
	{
		return( SESecurityManager.getTrustStore());
	}


	@Override
	public Certificate
	createSelfSignedCertificate(
		String		alias,
		String		cert_dn,
		int			strength )

		throws Exception
	{
		return( SESecurityManager.createSelfSignedCertificate(alias, cert_dn, strength ));
	}

	@Override
	public byte[]
	getIdentity()
	{
		return( core.getCryptoManager().getSecureID());
	}

	@Override
	public SEPublicKey
	getPublicKey(
		int		key_type,
		String	reason_resource )

		throws Exception
	{
		byte[]	encoded = core.getCryptoManager().getECCHandler().getPublicKey( reason_resource );

		return( new SEPublicKeyImpl( key_type, 1, encoded ));
	}

	@Override
	public SEPublicKey
	getPublicKey(
		int		key_type,
		int		instance,
		String	reason_resource )

		throws Exception
	{
		byte[]	encoded = core.getCryptoManager().getECCHandler( instance ).getPublicKey( reason_resource );

		return( new SEPublicKeyImpl( key_type, instance, encoded ));
	}
	

	@Override
	public GenericMessageConnection
	getSTSConnection(
		GenericMessageConnection	connection,
		SEPublicKey					my_public_key,
		SEPublicKeyLocator			key_locator,
		String						reason_resource,
		int							block_crypto )

		throws Exception
	{
		return( new SESTSConnectionImpl( core, (GenericMessageConnectionImpl)connection, my_public_key, key_locator, reason_resource, block_crypto ));
	}
}
