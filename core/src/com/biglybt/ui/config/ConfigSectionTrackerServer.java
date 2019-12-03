/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Constants;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.PasswordParameter;

import static com.biglybt.core.config.ConfigKeys.Tracker.*;

public class ConfigSectionTrackerServer
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "tracker.server";

	private final static int REQUIRED_MODE = Parameter.MODE_INTERMEDIATE;

	private ConfigDetailsCallback cbIPChecker;

	private ConfigDetailsCallback cbCreateCert;

	private ParameterListener trackerI2PHostPortListener;

	private ParameterListener trackerTorHostPortListener;

	public ConfigSectionTrackerServer() {
		super(SECTION_ID, ConfigSection.SECTION_TRACKER, REQUIRED_MODE);
	}

	public void init(ConfigDetailsCallback cbIPChecker,
			ConfigDetailsCallback cbCreateCert) {
		this.cbIPChecker = cbIPChecker;
		this.cbCreateCert = cbCreateCert;
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();
		if (trackerI2PHostPortListener != null) {
			COConfigurationManager.removeParameterListener(SCFG_TRACKER_I2P_HOST_PORT,
					trackerI2PHostPortListener);
			trackerI2PHostPortListener = null;
		}
		if (trackerTorHostPortListener != null) {
			COConfigurationManager.removeParameterListener(SCFG_TRACKER_TOR_HOST_PORT,
					trackerTorHostPortListener);
			trackerTorHostPortListener = null;
		}
	}

	@Override
	public void build() {

		setDefaultUserModeForAdd(Parameter.MODE_INTERMEDIATE);

		// MAIN TAB DATA

		StringParameterImpl tracker_ip = new StringParameterImpl(SCFG_TRACKER_IP,
				"ConfigView.section.tracker.ip");
		add(tracker_ip);
		tracker_ip.setWidthInCharacters(12);

		if (cbIPChecker != null) {
			ActionParameterImpl check_button = new ActionParameterImpl(null,
					"ConfigView.section.tracker.checkip");
			add(check_button);

			check_button.addListener(param -> cbIPChecker.run(mapPluginParams));

			ParameterGroupImpl pgIP = new ParameterGroupImpl(null, tracker_ip,
					check_button);
			pgIP.setNumberOfColumns(2);
			pgIP.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
			add("TS.pgIP", pgIP);
		}

		List<Parameter> listEnableOnNonSSL = new ArrayList<>();

		BooleanParameterImpl nonsslEnable = new BooleanParameterImpl(
				BCFG_TRACKER_PORT_ENABLE, "ConfigView.section.tracker.port");
		add(nonsslEnable);

		IntParameterImpl tracker_port = new IntParameterImpl(ICFG_TRACKER_PORT,
				null, 0, 65535);
		add(tracker_port, listEnableOnNonSSL);

		StringParameterImpl tracker_port_backup = new StringParameterImpl(
				SCFG_TRACKER_PORT_BACKUPS, "ConfigView.section.tracker.portbackup");
		add(tracker_port_backup, listEnableOnNonSSL);
		tracker_port_backup.setWidthInCharacters(15);

		// row

		List<Parameter> listEnableOnSSL = new ArrayList<>();

		BooleanParameterImpl sslEnable = new BooleanParameterImpl(
				BCFG_TRACKER_PORT_SSL_ENABLE, "ConfigView.section.tracker.sslport");
		add(sslEnable);

		IntParameterImpl tracker_port_ssl = new IntParameterImpl(
				ICFG_TRACKER_PORT_SSL, null, 0, 65535);
		add(tracker_port_ssl, listEnableOnSSL);

		StringParameterImpl tracker_port_ssl_backup = new StringParameterImpl(
				SCFG_TRACKER_PORT_SSL_BACKUPS, "ConfigView.section.tracker.portbackup");
		add(tracker_port_ssl_backup, listEnableOnSSL);
		tracker_port_ssl_backup.setWidthInCharacters(15);

		ParameterGroupImpl pgPorts = new ParameterGroupImpl(null, nonsslEnable,
				tracker_port, tracker_port_backup, sslEnable, tracker_port_ssl,
				tracker_port_ssl_backup);
		add("TS.pgPorts", pgPorts);
		pgPorts.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
		pgPorts.setNumberOfColumns(3);

		// create cert row

		if (cbCreateCert != null) {

			ActionParameterImpl cert_button = new ActionParameterImpl(
					"ConfigView.section.tracker.createcert",
					"ConfigView.section.tracker.createbutton");
			add(cert_button, listEnableOnSSL);

			cert_button.addListener(param -> cbCreateCert.run(mapPluginParams));

			final String linkFAQ = Constants.PLUGINS_WEB_SITE + "faq.php#19";
/* This FAQ url is invalid. Original text:
<h1>Tracker Security (Passwords and SSL)...</h1>
Access to the tracker web pages and the tracker announce process can be controlled by password settings specified on the Tracker configuration panel. This supports basic authentication and as such the user name and password values are transmitted in plain text. This can further be protected by using SSL (below). Note that password protecting the tracker announce process requires a BitTorrent client capable of handling authentication, such as Azureus.
Communication with the tracker can be encrypted using SSL, again this requires a suitable client such as Azureus.

Configuration of SSL is required for both the Azureus downloader and tracker:

**Tracker configuration**: It is necessary to generate a public/private key pair for the SSL framework and store this in a file called ".keystore" located in Azureus's home directory (where it stores the "azureus.config" file). It currently must have a keystore and key password of "changeit". Such a key pair can be generated via the following Java command ("keytool" can be found in the JRE bin directory).
keytool -genkey -keystore %home%\.keystore -keypass changeit -storepass changeit -keyalg rsa -alias azureus
Various questions are asked during the key generation process. The important one is the first one, "What is your first and last name?" Respond to this with the dns name (or IP address) of the tracker.
The certificate required for clients can then be exported via
* keytool -export -keystore %home%\.keystore -keypass changeit -storepass changeit -alias azureus -file azureus.cer
Note that it is the certificate, NOT the private key that is distributed for client use. Also note that it is possible to directly obtain the certificate from the SSL protected tracker if using Internet Explorer, as it allows you to save the certificate when contacting the site.

**Downloader configuration**: The client must trust the certificate in order to communicate with the site. The certificate must be imported on the client into a certificate store called ".certs" in the same place as ".keystore" above. The command to do this is:
* keytool -import -keystore %home%\.certs -alias azureus -file azureus.cer
assuming the certificate is in file "azureus.cer". Again the password must be "changeit". When prompted say "yes" to "do you trust this certificate".

A good way to test that the tracker and downloader setup is going to work is to seed a torrent on the SSL tracker. Note that this requires Azureus to be set up as both Tracker and downloader above, and hence the ".keystore" and ".certs" files must both be populated via the three steps of key generation, certificate export and certificate import.

When creating a torrent to host using SSL, check the SSL checkbox on the "create torrent" wizard. This ensures that the announce url starts with "https" (as opposed to "http") and also that the SSL port number is used (as opposed to the non-SSL port, again see the Tracker configuration).
 */

			HyperlinkParameterImpl cert_button_info = new HyperlinkParameterImpl(
					"ConfigView.section.tracker.sslport.info", linkFAQ);
			add(cert_button_info, listEnableOnSSL);

			ParameterGroupImpl pgCertButton = new ParameterGroupImpl(null,
					cert_button, cert_button_info);
			add("TS.pgCertButton", pgCertButton);
			pgCertButton.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
			pgCertButton.setNumberOfColumns(2);

		}

		sslEnable.addEnabledOnSelection(listEnableOnSSL.toArray(new Parameter[0]));

		// enable I2P

		BooleanParameterImpl i2p_enable = new BooleanParameterImpl(
				BCFG_TRACKER_I2P_ENABLE, "label.enable.i2p");
		add(i2p_enable, listEnableOnNonSSL);

		trackerI2PHostPortListener = parameterName -> {
			String val = "http://"
					+ COConfigurationManager.getStringParameter(parameterName)
					+ "/announce";
			i2p_enable.setSuffixLabelText(val);
		};
		COConfigurationManager.addAndFireParameterListener(
				SCFG_TRACKER_I2P_HOST_PORT, trackerI2PHostPortListener);

		// enable Tor

		BooleanParameterImpl tor_enable = new BooleanParameterImpl(
				BCFG_TRACKER_TOR_ENABLE, "label.enable.tor");
		add(tor_enable, listEnableOnNonSSL);

		trackerTorHostPortListener = parameterName -> {
			String val = "http://"
					+ COConfigurationManager.getStringParameter(parameterName)
					+ "/announce";
			tor_enable.setSuffixLabelText(val);
		};

		COConfigurationManager.addAndFireParameterListener(
				SCFG_TRACKER_TOR_HOST_PORT, trackerTorHostPortListener);

		nonsslEnable.addEnabledOnSelection(
				listEnableOnNonSSL.toArray(new Parameter[0]));

		// row

		BooleanParameterImpl tracker_public_enable = new BooleanParameterImpl(
				BCFG_TRACKER_PUBLIC_ENABLE, "ConfigView.section.tracker.publicenable");
		add(tracker_public_enable);
		// TODO: Get rid of mid-sentence /n in translations.
		tracker_public_enable.setSuffixLabelText(MessageText.getString(
				"ConfigView.section.tracker.publicenable.info").replaceAll("\n", " "));

		// row

		BooleanParameterImpl forcePortDetails = new BooleanParameterImpl(
				BCFG_TRACKER_PORT_FORCE_EXTERNAL,
				"ConfigView.section.tracker.forceport");
		add(forcePortDetails);

		com.biglybt.pif.ui.config.ParameterListener plForcePortDetails = param -> forcePortDetails.setEnabled(
				nonsslEnable.getValue() || sslEnable.getValue());
		nonsslEnable.addListener(plForcePortDetails);
		sslEnable.addListener(plForcePortDetails);
		plForcePortDetails.parameterChanged(null);

		// row
		// add announce urls to hosted torrents
		add(new BooleanParameterImpl(BCFG_TRACKER_HOST_ADD_OUR_ANNOUNCE_URLS,
				"ConfigView.section.tracker.host.addurls"));

		// row

		BooleanParameterImpl passwordEnableWeb = new BooleanParameterImpl(
				BCFG_TRACKER_PASSWORD_ENABLE_WEB,
				"ConfigView.section.tracker.passwordenableweb");
		add(passwordEnableWeb);

		BooleanParameterImpl passwordWebHTTPSOnly = new BooleanParameterImpl(
				BCFG_TRACKER_PASSWORD_WEB_HTTPS_ONLY,
				"ConfigView.section.tracker.passwordwebhttpsonly");
		add(passwordWebHTTPSOnly);
		passwordWebHTTPSOnly.setIndent(1, true);

		com.biglybt.pif.ui.config.ParameterListener psPasswordWebHTTPSOnly = param -> passwordWebHTTPSOnly.setEnabled(
				passwordEnableWeb.getValue() && sslEnable.getValue());
		passwordEnableWeb.addListener(psPasswordWebHTTPSOnly);
		sslEnable.addListener(psPasswordWebHTTPSOnly);
		psPasswordWebHTTPSOnly.parameterChanged(null);

		// row

		BooleanParameterImpl passwordEnableTorrent = new BooleanParameterImpl(
				BCFG_TRACKER_PASSWORD_ENABLE_TORRENT,
				"ConfigView.section.tracker.passwordenabletorrent");
		add(passwordEnableTorrent);
		passwordEnableTorrent.setSuffixLabelKey(
				"ConfigView.section.tracker.passwordenabletorrent.info");

		// row

		StringParameterImpl tracker_username = new StringParameterImpl(
				SCFG_TRACKER_USERNAME, "ConfigView.section.tracker.username");
		add(tracker_username);
		tracker_username.setWidthInCharacters(12);

		// row

		PasswordParameterImpl tracker_password = new PasswordParameterImpl(
				SCFG_TRACKER_PASSWORD, "ConfigView.section.tracker.password",
				PasswordParameter.ET_SHA1);
		add(tracker_password);
		tracker_password.setWidthInCharacters(20);

		com.biglybt.pif.ui.config.ParameterListener plEnableNamePW = (param) -> {
			boolean selected = passwordEnableWeb.getValue()
					|| passwordEnableTorrent.getValue();

			tracker_username.setEnabled(selected);
			tracker_password.setEnabled(selected);
		};
		passwordEnableWeb.addListener(plEnableNamePW);
		passwordEnableTorrent.addListener(plEnableNamePW);
		plEnableNamePW.parameterChanged(null);

		// Poll Group //

		List<Parameter> listPoll = new ArrayList<>();

		add(new IntParameterImpl(ICFG_TRACKER_POLL_INTERVAL_MIN,
				"ConfigView.section.tracker.pollintervalmin"), listPoll);

		add(new IntParameterImpl(ICFG_TRACKER_POLL_INTERVAL_MAX,
				"ConfigView.section.tracker.pollintervalmax"), listPoll);

		// row

		add(new IntParameterImpl(ICFG_TRACKER_POLL_INC_BY,
				"ConfigView.section.tracker.pollintervalincby"), listPoll);

		add(new IntParameterImpl(ICFG_TRACKER_POLL_INC_PER,
				"ConfigView.section.tracker.pollintervalincper"), listPoll);

		ParameterGroupImpl pgPoll = new ParameterGroupImpl(
				"ConfigView.section.tracker.pollinterval", listPoll);
		add("TS.pgPoll", pgPoll);
		pgPoll.setNumberOfColumns(2);

		// scrape + cache group

		List<Parameter> listScrapeCache = new ArrayList<>();

		// row

		add(new IntParameterImpl(ICFG_TRACKER_SCRAPE_RETRY_PERCENTAGE,
				"ConfigView.section.tracker.announcescrapepercentage"),
				listScrapeCache);

		add(new IntParameterImpl(ICFG_TRACKER_SCRAPE_CACHE,
				"ConfigView.section.tracker.scrapecacheperiod"), listScrapeCache);

		// row

		add(new IntParameterImpl(ICFG_TRACKER_ANNOUNCE_CACHE_MIN_PEERS,
				"ConfigView.section.tracker.announcecacheminpeers"), listScrapeCache);

		add(new IntParameterImpl(ICFG_TRACKER_ANNOUNCE_CACHE,
				"ConfigView.section.tracker.announcecacheperiod"), listScrapeCache);

		ParameterGroupImpl pgScrapeCache = new ParameterGroupImpl(
				"ConfigView.section.tracker.scrapeandcache", listScrapeCache);
		add("TS.pgScrapeCache", pgScrapeCache);
		pgScrapeCache.setNumberOfColumns(2);

		// main tab again

		add(new IntParameterImpl(ICFG_TRACKER_MAX_PEERS_RETURNED,
				"ConfigView.section.tracker.maxpeersreturned"));

		// seed retention limit

		IntParameterImpl tracker_max_seeds_retained = new IntParameterImpl(
				ICFG_TRACKER_MAX_SEEDS_RETAINED,
				"ConfigView.section.tracker.seedretention");
		add(tracker_max_seeds_retained);
		tracker_max_seeds_retained.setSuffixLabelKey(
				"ConfigView.section.tracker.seedretention.info");

		// row

		add(new BooleanParameterImpl(BCFG_TRACKER_NAT_CHECK_ENABLE,
				"ConfigView.section.tracker.natcheckenable"));
		// row

		IntParameterImpl tracker_nat_check_timeout = new IntParameterImpl(
				ICFG_TRACKER_NAT_CHECK_TIMEOUT,
				"ConfigView.section.tracker.natchecktimeout");
		add(tracker_nat_check_timeout);
		tracker_nat_check_timeout.setMinValue(1);
		tracker_nat_check_timeout.setIndent(1, false);

		// row

		add(new BooleanParameterImpl(BCFG_TRACKER_SEND_PEER_I_DS,
				"ConfigView.section.tracker.sendpeerids"));

		// row

		BooleanParameterImpl enable_udp = new BooleanParameterImpl(
				BCFG_TRACKER_PORT_UDP_ENABLE, "ConfigView.section.tracker.enableudp");
		add(enable_udp);

		// row

		IntParameterImpl udp_version = new IntParameterImpl(
				ICFG_TRACKER_PORT_UDP_VERSION, "ConfigView.section.tracker.udpversion");
		add(udp_version);
		udp_version.setIndent(1, true);

		enable_udp.addEnabledOnSelection(udp_version);

		// row

		add(new BooleanParameterImpl(BCFG_TRACKER_COMPACT_ENABLE,
				"ConfigView.section.tracker.enablecompact"));

		// row

		BooleanParameterImpl log_enable = new BooleanParameterImpl(
				BCFG_TRACKER_LOG_ENABLE, "ConfigView.section.tracker.logenable");
		add(log_enable);

		// row

		add(new BooleanParameterImpl(BCFG_TRACKER_KEY_ENABLE_SERVER,
				"ConfigView.section.tracker.enablekey"), Parameter.MODE_ADVANCED);

		//  banned peers

		add(new StringParameterImpl(SCFG_TRACKER_BANNED_CLIENTS,
				"ConfigView.section.tracker.banned.clients"), Parameter.MODE_ADVANCED);

		// Networks Group //

		List<Parameter> listNetworks = new ArrayList<>();

		add(new LabelParameterImpl(
				"ConfigView.section.tracker.server.group.networks.info"),
				Parameter.MODE_ADVANCED);

		for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

			String nn = AENetworkClassifier.AT_NETWORKS[i];

			String config_name = BCFG_PREFIX_TRACKER_NETWORK_SELECTION_DEFAULT + nn;
			String msg_text = "ConfigView.section.connection.networks." + nn;

			add(new BooleanParameterImpl(config_name, msg_text),
					Parameter.MODE_ADVANCED, listNetworks);
		}

		add(new ParameterGroupImpl(
				"ConfigView.section.tracker.server.group.networks", listNetworks),
				Parameter.MODE_ADVANCED);

		// processing limits group //

		List<Parameter> listProcessing = new ArrayList<>();

		// row annouce/scrape max process time

		IntParameterImpl tracker_max_get_time = new IntParameterImpl(
				ICFG_TRACKER_MAX_GET_TIME, "ConfigView.section.tracker.maxgettime");
		add(tracker_max_get_time, Parameter.MODE_ADVANCED, listProcessing);
		tracker_max_get_time.setSuffixLabelKey(
				"ConfigView.section.tracker.maxgettime.info");

		// row post multiplier

		IntParameterImpl tracker_max_post_time_multiplier = new IntParameterImpl(
				ICFG_TRACKER_MAX_POST_TIME_MULTIPLIER,
				"ConfigView.section.tracker.maxposttimemultiplier");
		add(tracker_max_post_time_multiplier, Parameter.MODE_ADVANCED,
				listProcessing);
		tracker_max_post_time_multiplier.setSuffixLabelKey(
				"ConfigView.section.tracker.maxposttimemultiplier.info");

		// row max threads

		add(new IntParameterImpl(ICFG_TRACKER_MAX_THREADS,
				"ConfigView.section.tracker.maxthreads", 1, 4096),
				Parameter.MODE_ADVANCED, listProcessing);

		add(new ParameterGroupImpl("ConfigView.section.tracker.processinglimits",
				listProcessing), Parameter.MODE_ADVANCED);

		// non-blocking tracker group //

		// row

		BooleanParameterImpl nb_enable = new BooleanParameterImpl(
				BCFG_TRACKER_TCP_NON_BLOCKING,
				"ConfigView.section.tracker.tcpnonblocking");
		add(nb_enable, Parameter.MODE_ADVANCED);

		// row max conc connections

		IntParameterImpl maxConcConn = new IntParameterImpl(
				ICFG_TRACKER_TCP_NON_BLOCKING_CONC_MAX,
				"ConfigView.section.tracker.nonblockingconcmax");
		add(maxConcConn, Parameter.MODE_ADVANCED);

		nb_enable.addEnabledOnSelection(maxConcConn);

		add(new ParameterGroupImpl("ConfigView.section.tracker.nonblocking",
				nb_enable, maxConcConn), Parameter.MODE_ADVANCED);

	}
}
