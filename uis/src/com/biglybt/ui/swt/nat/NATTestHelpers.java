/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.nat;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.function.Consumer;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipchecker.natchecker.NatChecker;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminProgressListener;
import com.biglybt.core.networkmanager.admin.NetworkAdminProtocol;
import com.biglybt.core.util.Debug;

public class 
NATTestHelpers
{
	public static void
	runTCP(
		int					TCPListenPort,
		Consumer<String>	logger )
	{
		logger.accept(MessageText.getString("configureWizard.nat.testing") + " TCP " + TCPListenPort + " ... ");

		InetAddress[] bind_ips = NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true);

		{
			// IPv4

			InetAddress bind = bind_ips[0];

			if ( !bind.isAnyLocalAddress()){

				for ( InetAddress a: bind_ips ){

					if ( a instanceof Inet4Address ){

						bind = a;

						break;
					}
				}
			}

			NatChecker checker =
					new NatChecker(
							CoreFactory.getSingleton(), 
							bind, 
							TCPListenPort, 
							false,		// ipv6  
							false );

			switch (checker.getResult()) {
				case NatChecker.NAT_OK :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ok") + " (" + checker.getExternalAddress().getHostAddress() + ")\n" + checker.getAdditionalInfo());
					break;
				case NatChecker.NAT_KO :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ko") + " - " + checker.getAdditionalInfo()+".\n");
					break;
				default :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.unable") + ". \n(" + checker.getAdditionalInfo()+").\n");
					break;
			}
		}

		if ( NetworkAdmin.getSingleton().hasIPV6Potential()){

			InetAddress bind = bind_ips[0];

			if ( !bind.isAnyLocalAddress()){

				for ( InetAddress a: bind_ips ){

					if ( a instanceof Inet6Address ){

						bind = a;

						break;
					}
				}
			}

			logger.accept("\n" + MessageText.getString("configureWizard.nat.testing") + " TCP " + TCPListenPort + " IPv6 ... ");
			
			NatChecker checker =
					new NatChecker(
							CoreFactory.getSingleton(), 
							bind, 
							TCPListenPort, 
							true,		// ipv6  
							false );

			switch (checker.getResult()) {
				case NatChecker.NAT_OK :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ok") + " (" + checker.getExternalAddress().getHostAddress() + ")\n" + checker.getAdditionalInfo());
					break;
				case NatChecker.NAT_KO :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ko") + " - " + checker.getAdditionalInfo()+".\n");
					break;
				default :
					logger.accept( "\n" + MessageText.getString("configureWizard.nat.unable") + ". \n(" + checker.getAdditionalInfo()+").\n");
					break;
			}
		}
	}

	public static void
	runUDP(
			Core				core,
			int					udp_port,
			Consumer<String>	logger )
	{
		final NetworkAdmin	admin = NetworkAdmin.getSingleton();

		NetworkAdminProtocol[] inbound_protocols = admin.getInboundProtocols(core);

		NetworkAdminProtocol selected = null;

		for ( NetworkAdminProtocol p: inbound_protocols ){

			if ( p.getType() == NetworkAdminProtocol.PT_UDP && p.getPort() == udp_port ){

				selected = p;

				break;
			}
		}

		if ( selected == null ){

			selected = admin.createInboundProtocol( core, NetworkAdminProtocol.PT_UDP, udp_port );
		}

		if ( selected == null ){

			logger.accept( "\n" + MessageText.getString("configureWizard.nat.ko") + ". \n( No UDP protocols enabled ).\n");

		}else{

			logger.accept(MessageText.getString("configureWizard.nat.testing") + " UDP " + udp_port + " ... ");

			try{
				InetAddress result = selected.test(
						null,
						false,
						true,
						new NetworkAdminProgressListener()
						{
							@Override
							public void
							reportProgress(
									String task )
							{
								logger.accept( "\n    " + task );
							}
						});

				logger.accept( "\n" + MessageText.getString("configureWizard.nat.ok") + " (" + result.getHostAddress() + ")\n");

			}catch( Throwable e ){

				logger.accept( "\n" + MessageText.getString("configureWizard.nat.ko") + ". " + Debug.getNestedExceptionMessage(e)+".\n");
			}

			if ( NetworkAdmin.getSingleton().hasIPV6Potential()){

				logger.accept( "\n" + MessageText.getString("configureWizard.nat.testing") + " UDP " + udp_port + " IPv6 ... ");

				try{
					InetAddress result = selected.test(
							null,
							true,
							true,
							new NetworkAdminProgressListener()
							{
								@Override
								public void
								reportProgress(
										String task )
								{
									logger.accept( "\n    " + task );
								}
							});

					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ok") + " (" + result.getHostAddress() + ")\n");

				}catch( Throwable e ){

					logger.accept( "\n" + MessageText.getString("configureWizard.nat.ko") + ". " + Debug.getNestedExceptionMessage(e)+".\n");
				}
			}
		}
	}
}
