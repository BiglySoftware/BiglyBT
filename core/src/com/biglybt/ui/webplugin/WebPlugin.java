/*
 * File    : WebPlugin.java
 * Created : 23-Jan-2004
 * By      : parg
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

package com.biglybt.ui.webplugin;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import org.json.simple.JSONObject;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.pairing.*;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.*;
import com.biglybt.pif.ipfilter.IPRange;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerException;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.web.*;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.components.UITextArea;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.plugin.upnp.UPnPMapping;
import com.biglybt.plugin.upnp.UPnPPlugin;
import com.biglybt.util.JSONUtils;

// XXX Used by webui plugins
public class
WebPlugin
	implements Plugin, TrackerWebPageGenerator
{
	public static final String	PR_ENABLE					= "Enable";						// Boolean
	public static final String	PR_DISABLABLE				= "Disablable";					// Boolean
	public static final String	PR_PORT						= "Port";						// Integer
	public static final String	PR_BIND_IP					= "Bind IP";					// String
	public static final String	PR_ROOT_RESOURCE			= "Root Resource";				// String
	public static final String 	PR_HOME_PAGE				= "Home Page";					// String
	public static final String	PR_ROOT_DIR					= "Root Dir";					// String
	public static final String	PR_ACCESS					= "Access";						// String
	public static final String	PR_LOG						= "DefaultLoggerChannel";		// LoggerChannel
	public static final String	PR_CONFIG_MODEL_PARAMS		= "DefaultConfigModelParams";	// String[] params to use when creating config model
	public static final String	PR_CONFIG_MODEL				= "DefaultConfigModel";			// BasicPluginConfigModel
	public static final String	PR_VIEW_MODEL				= "DefaultViewModel";			// BasicPluginViewModel
	public static final String	PR_HIDE_RESOURCE_CONFIG		= "DefaultHideResourceConfig";	// Boolean
	public static final String	PR_ENABLE_KEEP_ALIVE		= "DefaultEnableKeepAlive";		// Boolean
	public static final String	PR_PAIRING_SID				= "PairingSID";					// String
	public static final String	PR_NON_BLOCKING				= "NonBlocking";				// Boolean
	public static final String	PR_ENABLE_PAIRING			= "EnablePairing";				// Boolean
	public static final String	PR_ENABLE_I2P				= "EnableI2P";					// Boolean
	public static final String	PR_ENABLE_TOR				= "EnableTor";					// Boolean
	public static final String	PR_ENABLE_UPNP				= "EnableUPNP";					// Boolean

	public static final String	PROPERTIES_MIGRATED		= "Properties Migrated";
	//public static final String	CONFIG_MIGRATED			= "Config Migrated";
	public static final String	PAIRING_MIGRATED		= "Pairing Migrated";
	public static final String	PAIRING_SESSION_KEY		= "Pairing Session Key";

	public static final String	CONFIG_PASSWORD_ENABLE			= "Password Enable";
	public static final boolean	CONFIG_PASSWORD_ENABLE_DEFAULT	= false;

	public static final String	CONFIG_NO_PW_WHITELIST			= "Password Disabled Whitelist";
	public static final String	CONFIG_NO_PW_WHITELIST_DEFAULT	= "localhost, 127.0.0.1, [::1], $";
			
	public static final String	CONFIG_PAIRING_ENABLE			= "Pairing Enable";
	public static final boolean	CONFIG_PAIRING_ENABLE_DEFAULT	= true;

	public static final String	CONFIG_PORT_OVERRIDE			= "Port Override";

	public static final String	CONFIG_PAIRING_AUTO_AUTH			= "Pairing Auto Auth";
	public static final boolean	CONFIG_PAIRING_AUTO_AUTH_DEFAULT	= true;


	public static final String	CONFIG_ENABLE					= PR_ENABLE;
	public  			boolean	CONFIG_ENABLE_DEFAULT			= true;

	public static final String	CONFIG_USER						= "User";
	public static final String	CONFIG_USER_DEFAULT				= "";

	public static final String	CONFIG_PASSWORD					= "Password";
	public static final byte[]	CONFIG_PASSWORD_DEFAULT			= {};

	public static final String 	CONFIG_PORT						= PR_PORT;
	public int			 		CONFIG_PORT_DEFAULT				= 8089;

	public static final String 	CONFIG_BIND_IP					= PR_BIND_IP;
	public String		 		CONFIG_BIND_IP_DEFAULT			= "";

	public static final String 	CONFIG_PROTOCOL					= "Protocol";
	public static final String 	CONFIG_PROTOCOL_DEFAULT			= "HTTP";

	public static final String	CONFIG_UPNP_ENABLE				= "UPnP Enable";
	public 				boolean	CONFIG_UPNP_ENABLE_DEFAULT		= true;

	public static final String 	CONFIG_HOME_PAGE				= PR_HOME_PAGE;
	public  		 String 	CONFIG_HOME_PAGE_DEFAULT		= "index.html";

	public static final String 	CONFIG_ROOT_DIR					= PR_ROOT_DIR;
	public        		String 	CONFIG_ROOT_DIR_DEFAULT			= "";

	public static final String 	CONFIG_ROOT_RESOURCE			= PR_ROOT_RESOURCE;
	public              String 	CONFIG_ROOT_RESOURCE_DEFAULT	= "";

	public static final String 	CONFIG_MODE						= "Mode";
	public static final String 	CONFIG_MODE_FULL				= "full";
	public static final String 	CONFIG_MODE_DEFAULT				= CONFIG_MODE_FULL;

	public static final String 	CONFIG_ACCESS					= PR_ACCESS;
	public        		String 	CONFIG_ACCESS_DEFAULT			= "all";

	protected static final String	NL			= "\r\n";

	protected static final String[]		welcome_pages = { "index.html", "index.htm", "index.php", "index.tmpl" };
	protected static File[]				welcome_files;

	private static final AsyncDispatcher	network_dispatcher = new AsyncDispatcher( "webplugin:netdispatch", 5000 );

	protected PluginInterface			plugin_interface;	// unfortunately this is accessed by webui - fix sometime

	private LoggerChannel			log;
	private PluginConfig plugin_config;
	private BasicPluginViewModel 	view_model;
	private BasicPluginConfigModel	config_model;

	private String					p_sid;

	private StringParameter			param_home;
	private StringParameter			param_rootdir;
	private StringParameter			param_rootres;

	private IntParameter			param_port;
	private StringListParameter		param_protocol;
	private StringParameter			param_bind;

	private StringParameter			param_access;

	private InfoParameter			param_i2p_dest;
	private InfoParameter			param_tor_dest;

	private BooleanParameter		p_upnp_enable;

	private BooleanParameter		pw_enable;
	private StringParameter			p_user_name;
	private PasswordParameter		p_password;
	private StringParameter			p_no_pw_whitelist;

	private BooleanParameter		param_auto_auth;
	private IntParameter			param_port_or;
	private boolean					setting_auto_auth;
	private String					pairing_access_code;
	private String					pairing_session_code;

	private boolean				plugin_enabled;

	private boolean				na_intf_listener_added;

	private String				home_page;
	private File				file_root;
	private String				resource_root;

	private String				root_dir;

	private boolean				ip_range_all	= false;
	private List<IPRange>		ip_ranges;

	private TrackerWebContext			tracker_context;
	private UPnPMapping					upnp_mapping;
	private PairingManagerListener		pairing_listener;

	private Properties	properties;

	private static ThreadLocal<String>		tls	=
		new ThreadLocal<String>()
		{
			@Override
			public String
			initialValue()
			{
				return( null );
			}
		};

	private static final int	LOGOUT_GRACE_MILLIS	= 5*1000;
	private static final String	GRACE_PERIOD_MARKER	= "<grace_period>";

	private Map<String,Long>	logout_timer 		= new HashMap<>();

	private boolean	unloaded;

	public
	WebPlugin()
	{
		properties	= new Properties();
	}

	public
	WebPlugin(
		Properties		defaults )
	{
		properties	= defaults;
	}

	@Override
	public void
	initialize(
		PluginInterface _plugin_interface )

		throws PluginException
	{
		plugin_interface	= _plugin_interface;

		plugin_config = plugin_interface.getPluginconfig();

		Properties plugin_properties = plugin_interface.getPluginProperties();

		if ( plugin_properties != null ){

			Object o = plugin_properties.get( "plugin." + PR_ROOT_DIR.replaceAll( " ", "_" ));

			if ( o instanceof String ){

				properties.put( PR_ROOT_DIR, o );
			}
		}

		Boolean	pr_enable = (Boolean)properties.get(PR_ENABLE);

		if ( pr_enable != null ){

			CONFIG_ENABLE_DEFAULT	= pr_enable.booleanValue();
		}

		Integer	pr_port = (Integer)properties.get(PR_PORT);

		if ( pr_port != null ){

			CONFIG_PORT_DEFAULT	= pr_port.intValue();
		}

		String	pr_bind_ip = (String)properties.get(PR_BIND_IP);

		if ( pr_bind_ip != null ){

			CONFIG_BIND_IP_DEFAULT	= pr_bind_ip.trim();
		}

		String	pr_root_resource = (String)properties.get( PR_ROOT_RESOURCE );

		if( pr_root_resource != null ){

			CONFIG_ROOT_RESOURCE_DEFAULT	= pr_root_resource;
		}

		String	pr_home_page = (String)properties.get( PR_HOME_PAGE );

		if( pr_home_page != null ){

			CONFIG_HOME_PAGE_DEFAULT		= pr_home_page;
		}

		String	pr_root_dir = (String)properties.get( PR_ROOT_DIR );

		if( pr_root_dir != null ){

			CONFIG_ROOT_DIR_DEFAULT	= pr_root_dir;
		}

		String	pr_access = (String)properties.get( PR_ACCESS );

		if( pr_access != null ){

			CONFIG_ACCESS_DEFAULT	= pr_access;
		}

		Boolean	pr_enable_upnp = (Boolean)properties.get(PR_ENABLE_UPNP);

		if ( pr_enable_upnp != null ){

			CONFIG_UPNP_ENABLE_DEFAULT	= pr_enable_upnp.booleanValue();
		}

		Boolean	pr_hide_resource_config = (Boolean)properties.get( PR_HIDE_RESOURCE_CONFIG );

		log = (LoggerChannel)properties.get( PR_LOG );

		if ( log == null ){

			log = plugin_interface.getLogger().getChannel("WebPlugin");
		}

		Boolean prop_pairing_enable = (Boolean)properties.get( PR_ENABLE_PAIRING );

		if ( prop_pairing_enable == null || prop_pairing_enable ){

				// default is based on sid availability

			p_sid = (String)properties.get( PR_PAIRING_SID );
		}

		UIManager	ui_manager = plugin_interface.getUIManager();

		view_model = (BasicPluginViewModel)properties.get( PR_VIEW_MODEL );

		if ( view_model == null ){

			view_model = ui_manager.createBasicPluginViewModel( plugin_interface.getPluginName());
		}

		String plugin_id = plugin_interface.getPluginID();

		String sConfigSectionID = "plugins." + plugin_id;

		view_model.setConfigSectionID(sConfigSectionID);
		view_model.getStatus().setText( "Running" );
		view_model.getActivity().setVisible( false );
		view_model.getProgress().setVisible( false );

		log.addListener(
			new LoggerChannelListener()
			{
				@Override
				public void
				messageLogged(
					int		type,
					String	message )
				{
					log( message+"\n");
				}

				@Override
				public void
				messageLogged(
					String		str,
					Throwable	error )
				{
					log( str + "\n" );
					log( error.toString() + "\n" );
				}
				
				private void
				log(
					String		str )
				{
					UITextArea area = view_model.getLogArea();
					
					if ( area != null ) {
						
						area.appendText( str );
					}
					
				}
			});


		config_model = (BasicPluginConfigModel)properties.get( PR_CONFIG_MODEL );

		if ( config_model == null ){

			String[] cm_params = (String[])properties.get( PR_CONFIG_MODEL_PARAMS );

			if ( cm_params == null || cm_params.length == 0 ){

				config_model = ui_manager.createBasicPluginConfigModel(ConfigSection.SECTION_PLUGINS, sConfigSectionID);

			}else if ( cm_params.length == 1 ){

				config_model = ui_manager.createBasicPluginConfigModel( cm_params[0] );

			}else{

				config_model = ui_manager.createBasicPluginConfigModel( cm_params[0], cm_params[1] );
			}
		}


		/* removed 2017/12/01 - no longer needed 
	
		boolean	save_needed = false;

		if ( !plugin_config.getPluginBooleanParameter( CONFIG_MIGRATED, false )){

			plugin_config.setPluginParameter( CONFIG_MIGRATED, true );

			save_needed	= true;

			plugin_config.setPluginParameter(
					CONFIG_PASSWORD_ENABLE,
					plugin_config.getUnsafeBooleanParameter(
							"Tracker Password Enable Web", CONFIG_PASSWORD_ENABLE_DEFAULT ));

			plugin_config.setPluginParameter(
					CONFIG_USER,
					plugin_config.getUnsafeStringParameter(
							"Tracker Username", CONFIG_USER_DEFAULT ));

			plugin_config.setPluginParameter(
					CONFIG_PASSWORD,
					plugin_config.getUnsafeByteParameter(
							"Tracker Password", CONFIG_PASSWORD_DEFAULT ));

		}

		if ( !plugin_config.getPluginBooleanParameter( PROPERTIES_MIGRATED, false )){

			plugin_config.setPluginParameter( PROPERTIES_MIGRATED, true );

			Properties	props = plugin_interface.getPluginProperties();

				// make sure we've got an old properties file too

			if ( props.getProperty( "port", "" ).length() > 0 ){

				save_needed = true;

				String	prop_port		= props.getProperty( "port",			""+CONFIG_PORT_DEFAULT );
				String	prop_protocol	= props.getProperty( "protocol", 		CONFIG_PROTOCOL_DEFAULT );
				String	prop_home		= props.getProperty( "homepage", 		CONFIG_HOME_PAGE_DEFAULT );
				String	prop_rootdir	= props.getProperty( "rootdir", 		CONFIG_ROOT_DIR_DEFAULT );
				String	prop_rootres	= props.getProperty( "rootresource", 	CONFIG_ROOT_RESOURCE_DEFAULT );
				String	prop_mode		= props.getProperty( "mode", 			CONFIG_MODE_DEFAULT );
				String	prop_access		= props.getProperty( "access", 			CONFIG_ACCESS_DEFAULT );

				int	prop_port_int = CONFIG_PORT_DEFAULT;

				try{
					prop_port_int	= Integer.parseInt( prop_port );

				}catch( Throwable e ){
				}

				plugin_config.setPluginParameter(CONFIG_PORT, prop_port_int );
				plugin_config.setPluginParameter(CONFIG_PROTOCOL, prop_protocol );
				plugin_config.setPluginParameter(CONFIG_HOME_PAGE, prop_home );
				plugin_config.setPluginParameter(CONFIG_ROOT_DIR, prop_rootdir );
				plugin_config.setPluginParameter(CONFIG_ROOT_RESOURCE, prop_rootres );
				plugin_config.setPluginParameter(CONFIG_MODE, prop_mode );
				plugin_config.setPluginParameter(CONFIG_ACCESS, prop_access );

				File	props_file = FileUtil.newFile( plugin_interface.getPluginDirectoryName(), "plugin.properties" );

				PrintWriter pw = null;

				try{
					File	backup = FileUtil.newFile( plugin_interface.getPluginDirectoryName(), "plugin.properties.bak" );

					props_file.renameTo( backup );

					pw = new PrintWriter( new FileWriter( props_file ));

					pw.println( "plugin.class=" + props.getProperty( "plugin.class" ));
					pw.println( "plugin.name=" + props.getProperty( "plugin.name" ));
					pw.println( "plugin.version=" + props.getProperty( "plugin.version" ));
					pw.println( "plugin.id=" + props.getProperty( "plugin.id" ));
					pw.println( "" );
					pw.println( "# configuration has been migrated to plugin config - see view->config->plugins" );
					pw.println( "# in the SWT user interface" );

					log.logAlert( 	LoggerChannel.LT_INFORMATION,
							plugin_interface.getPluginName() + " - plugin.properties settings migrated to plugin configuration." );

				}catch( Throwable  e ){

					Debug.printStackTrace( e );

					log.logAlert( 	LoggerChannel.LT_ERROR,
									plugin_interface.getPluginName() + " - plugin.properties settings migration failed." );

				}finally{

					if ( pw != null ){

						pw.close();
					}
				}
			}
		}

		if ( save_needed ){

			plugin_config.save();
		}
		*/
		
		Boolean	disablable = (Boolean)properties.get( PR_DISABLABLE );

		final BooleanParameter	param_enable;

		if ( disablable != null && disablable ){

			param_enable =
				config_model.addBooleanParameter2( CONFIG_ENABLE, "webui.enable", CONFIG_ENABLE_DEFAULT );

			plugin_enabled = param_enable.getValue();

		}else{
			param_enable 	= null;

			plugin_enabled 	= true;
		}

		initStage(1);

			// connection group

		param_port = config_model.addIntParameter2(		CONFIG_PORT, "webui.port", CONFIG_PORT_DEFAULT );

		param_port.setGenerateIntermediateEvents( false );

		param_bind = config_model.addStringParameter2(	CONFIG_BIND_IP, "webui.bindip", CONFIG_BIND_IP_DEFAULT );

		param_bind.setGenerateIntermediateEvents( false );

		param_protocol =
			config_model.addStringListParameter2(
					CONFIG_PROTOCOL, "webui.protocol", new String[]{ "http", "https" }, CONFIG_PROTOCOL_DEFAULT );

		param_protocol.setGenerateIntermediateEvents( false );

		ParameterListener update_server_listener =
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					setupServer();
				}
			};

		param_port.addListener( update_server_listener );
		param_bind.addListener( update_server_listener );
		param_protocol.addListener( update_server_listener );

		param_i2p_dest = config_model.addInfoParameter2( "webui.i2p_dest", "" );
		param_i2p_dest.setVisible( false );

		param_tor_dest = config_model.addInfoParameter2( "webui.tor_dest", "" );
		param_tor_dest.setVisible( false );

		if ( param_enable != null ){
			COConfigurationManager.registerExportedParameter( plugin_id + ".enable", param_enable.getConfigKeyName());
		}
		COConfigurationManager.registerExportedParameter( plugin_id + ".port", param_port.getConfigKeyName());
		COConfigurationManager.registerExportedParameter( plugin_id + ".protocol", param_protocol.getConfigKeyName());

		p_upnp_enable =
			config_model.addBooleanParameter2(
							CONFIG_UPNP_ENABLE,
							"webui.upnpenable",
							CONFIG_UPNP_ENABLE_DEFAULT );

		p_upnp_enable.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					setupUPnP();
				}
			});

		plugin_interface.addListener(
				new PluginListener()
				{
					@Override
					public void
					initializationComplete()
					{
						setupUPnP();
					}

					@Override
					public void
					closedownInitiated()
					{
					}

					@Override
					public void
					closedownComplete()
					{
					}
				});


		final LabelParameter		pairing_info;
		final BooleanParameter		pairing_enable;
		final HyperlinkParameter	pairing_test;
		final HyperlinkParameter	connection_test;

		if ( p_sid != null ){

			final PairingManager pm = PairingManagerFactory.getSingleton();

			pairing_info = config_model.addLabelParameter2( "webui.pairing.info." + (pm.isEnabled()?"y":"n"));

			pairing_enable = config_model.addBooleanParameter2( CONFIG_PAIRING_ENABLE, "webui.pairingenable", CONFIG_PAIRING_ENABLE_DEFAULT );

			if ( !plugin_config.getPluginBooleanParameter( PAIRING_MIGRATED, false )){

					// if they already have a password, don't override it by setting auto-auth

				boolean	has_pw_enabled = plugin_config.getPluginBooleanParameter( CONFIG_PASSWORD_ENABLE, CONFIG_PASSWORD_ENABLE_DEFAULT );

				if ( has_pw_enabled ){

					plugin_config.setPluginParameter( CONFIG_PAIRING_AUTO_AUTH, false );
				}

				plugin_config.setPluginParameter( PAIRING_MIGRATED, true );
			}

			param_port_or	=  config_model.addIntParameter2( CONFIG_PORT_OVERRIDE, "webui.port.override", 0 );

			param_auto_auth = config_model.addBooleanParameter2( CONFIG_PAIRING_AUTO_AUTH, "webui.pairing.autoauth", CONFIG_PAIRING_AUTO_AUTH_DEFAULT );

			param_auto_auth.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter param )
					{
						if ( pairing_enable.getValue() && pm.isEnabled()){

							setupAutoAuth();

						}else{

							setupSessionCode( null );
						}
					}
				});

			connection_test = config_model.addHyperlinkParameter2( "webui.connectiontest", getConnectionTestURL( p_sid ));

			URL server_url = PairingManagerFactory.getSingleton().getWebRemoteURL();

			pairing_test = config_model.addHyperlinkParameter2( "webui.pairingtest", server_url.toExternalForm() + "?sid=" + p_sid );

				// listeners setup later as they depend on userame params etc

			String sid_key =  "Plugin." + plugin_id + ".pairing.sid";

			COConfigurationManager.setStringDefault( sid_key, p_sid );

			COConfigurationManager.registerExportedParameter( plugin_id + ".pairing.sid", sid_key);
			COConfigurationManager.registerExportedParameter( plugin_id + ".pairing.enable", pairing_enable.getConfigKeyName());
			COConfigurationManager.registerExportedParameter( plugin_id + ".pairing.auto_auth", param_auto_auth.getConfigKeyName());

		}else{
			pairing_info	= null;
			pairing_enable 	= null;
			param_auto_auth	= null;
			param_port_or	= null;
			pairing_test	= null;
			connection_test	= null;
		}

		config_model.createGroup(
				"ConfigView.section.Pairing",
				new Parameter[]{
					pairing_info, pairing_enable, param_port_or, param_auto_auth, connection_test, pairing_test,
				});

		config_model.createGroup(
			"ConfigView.section.server",
			new Parameter[]{
				param_port, param_bind, param_protocol, param_i2p_dest, param_tor_dest, p_upnp_enable,
			});

		param_home 		= config_model.addStringParameter2(	CONFIG_HOME_PAGE, "webui.homepage", CONFIG_HOME_PAGE_DEFAULT );
		param_rootdir 	= config_model.addStringParameter2(	CONFIG_ROOT_DIR, "webui.rootdir", CONFIG_ROOT_DIR_DEFAULT );
		param_rootres	= config_model.addStringParameter2(	CONFIG_ROOT_RESOURCE, "webui.rootres", CONFIG_ROOT_RESOURCE_DEFAULT );

		if ( pr_hide_resource_config != null && pr_hide_resource_config.booleanValue()){

			param_home.setVisible( false );
			param_rootdir.setVisible( false );
			param_rootres.setVisible( false );

		}else{

			ParameterListener update_resources_listener =
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter param )
					{
						setupResources();
					}
				};

			param_home.addListener( update_resources_listener );
			param_rootdir.addListener( update_resources_listener );
			param_rootres.addListener( update_resources_listener );
		}

			// access group

		LabelParameter a_label1 = config_model.addLabelParameter2( "webui.mode.info" );
		StringListParameter param_mode =
			config_model.addStringListParameter2(
					CONFIG_MODE, "webui.mode", new String[]{ "full", "view" }, CONFIG_MODE_DEFAULT );


		LabelParameter a_label2 = config_model.addLabelParameter2( "webui.access.info" );

		param_access	= config_model.addStringParameter2(	CONFIG_ACCESS, "webui.access", CONFIG_ACCESS_DEFAULT );

		param_access.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					setupAccess();
				}
			});

		pw_enable =
			config_model.addBooleanParameter2(
							CONFIG_PASSWORD_ENABLE,
							"webui.passwordenable",
							CONFIG_PASSWORD_ENABLE_DEFAULT );

		p_user_name =
			config_model.addStringParameter2(
							CONFIG_USER,
							"webui.user",
							CONFIG_USER_DEFAULT );

		p_password =
			config_model.addPasswordParameter2(
							CONFIG_PASSWORD,
							"webui.password",
							PasswordParameter.ET_SHA1,
							CONFIG_PASSWORD_DEFAULT );

		p_no_pw_whitelist =
				config_model.addStringParameter2(
								CONFIG_NO_PW_WHITELIST,
								"webui.nopwwhitelist",
								CONFIG_NO_PW_WHITELIST_DEFAULT );
		
		pw_enable.addEnabledOnSelection( p_user_name );
		pw_enable.addEnabledOnSelection( p_password );

		pw_enable.addDisabledOnSelection(  p_no_pw_whitelist );
		
		ParameterListener auth_change_listener =
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter param )
				{
					if ( param_auto_auth != null ){

						if ( !setting_auto_auth ){

							log( "Disabling pairing auto-authentication as overridden by user" );

							param_auto_auth.setValue( false );
						}
					}

					if ( param == p_user_name || param == p_password ){

						setupSessionCode( null );
					}
				}
			};

		p_user_name.addListener( auth_change_listener );
		p_password.addListener( auth_change_listener );
		pw_enable.addListener( auth_change_listener );

		config_model.createGroup(
			"webui.group.access",
			new Parameter[]{
				a_label1, param_mode, a_label2, param_access,
				pw_enable, p_user_name, p_password, p_no_pw_whitelist,
			});

		if ( p_sid != null ){

			final PairingManager pm = PairingManagerFactory.getSingleton();

			pairing_enable.addListener(
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							Parameter param )
						{
							boolean enabled = pairing_enable.getValue();

							param_auto_auth.setEnabled( pm.isEnabled() && enabled );
							param_port_or.setEnabled( pm.isEnabled() && enabled );

							boolean test_ok = pm.isEnabled() && pairing_enable.getValue() && pm.peekAccessCode() != null && !pm.hasActionOutstanding();

							pairing_test.setEnabled( test_ok );
							connection_test.setEnabled( test_ok );

							setupPairing( p_sid, enabled );
						}
					});

			pairing_listener =
				new PairingManagerListener()
				{
					@Override
					public void
					somethingChanged(
						PairingManager pm )
					{
						pairing_info.setLabelKey( "webui.pairing.info." + (pm.isEnabled()?"y":"n"));

						if ( plugin_enabled ){

							pairing_enable.setEnabled( pm.isEnabled());

							param_auto_auth.setEnabled( pm.isEnabled() && pairing_enable.getValue() );
							param_port_or.setEnabled( pm.isEnabled() && pairing_enable.getValue() );

							boolean test_ok = pm.isEnabled() && pairing_enable.getValue() && pm.peekAccessCode() != null && !pm.hasActionOutstanding();

							pairing_test.setEnabled( test_ok );
							connection_test.setEnabled( test_ok );
						}

						connection_test.setHyperlink( getConnectionTestURL( p_sid ));

						setupPairing( p_sid, pairing_enable.getValue());
					}
				};

			pairing_listener.somethingChanged( pm );

			pm.addListener( pairing_listener );

			setupPairing( p_sid, pairing_enable.getValue());

			ParameterListener update_pairing_listener =
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter param )
					{
						updatePairing( p_sid );

						setupUPnP();
					}
				};

			param_port.addListener( update_pairing_listener );

			param_port_or.addListener( update_pairing_listener );

			param_protocol.addListener( update_pairing_listener );

			/*
			config_model.addActionParameter2( "test", "test" ).addListener(
				new ParameterListener()
				{
					public void
					parameterChanged(
						Parameter param )
					{
						try{
							pm.testService(
								p_sid,
								new PairingTestListener()
								{
									public void
									testStarted(
										PairingTest test )
									{
										System.out.println( "Test starts" );
									}

									public void
									testComplete(
										PairingTest test)
									{
										System.out.println( "Test complete: " + test.getOutcome() + "/" + test.getErrorMessage());
									}
								});
						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				});
			*/
		}

		if ( param_enable != null ){

			final List<Parameter> changed_params = new ArrayList<>();

			if ( !plugin_enabled){

				Parameter[] params = config_model.getParameters();

				for ( Parameter param: params ){

					if ( param == param_enable ){

						continue;
					}

					if ( param.isEnabled()){

						changed_params.add( param );

						param.setEnabled( false );
					}
				}
			}

			param_enable.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter e_p )
					{
							// this doesn't quite work as tne enabler/disabler parameter logic is implemented
							// badly and only toggles the UI component, not the enabled state of the
							// underlying parameter. grr. better than nothing though

						plugin_enabled = ((BooleanParameter)e_p).getValue();

						if ( plugin_enabled ){

							for ( Parameter p: changed_params ){

								p.setEnabled( true );
							}
						}else{

							changed_params.clear();

							Parameter[] params = config_model.getParameters();

							for ( Parameter param: params ){

								if ( param == e_p ){

									continue;
								}

								if ( param.isEnabled()){

									changed_params.add( param );

									param.setEnabled( false );
								}
							}
						}

						setupServer();

						setupUPnP();

						if ( p_sid != null ){

							setupPairing( p_sid, pairing_enable.getValue());
						}
					}
				});
		}

			// end config

		setupResources();

		setupAccess();

		setupServer();
	}

	protected void
	initStage(
		int	num )
	{
	}

	private String
	getConnectionTestURL(
		String		sid )
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		URL url = pm.getServiceURL();
			
		String res = url.toExternalForm() + "/web/test?sid=" + sid;

		if ( pm.isEnabled()){

			String ac = pm.peekAccessCode();

			if ( ac != null ){

				res += "&ac=" + ac;
			}
		}

		return( res );
	}

	protected boolean
	isPluginEnabled()
	{
		return( plugin_enabled );
	}

	protected void
	unloadPlugin()
	{
		if ( view_model != null ){

			view_model.destroy();

			view_model = null;
		}

		if ( config_model != null ){

			config_model.destroy();

			config_model = null;
		}

		if ( tracker_context != null ){

			tracker_context.destroy();

			tracker_context = null;
		}

		if ( upnp_mapping != null ){

			upnp_mapping.destroy();

			upnp_mapping = null;
		}

		if ( pairing_listener != null ){

			PairingManager pm = PairingManagerFactory.getSingleton();

			pm.removeListener( pairing_listener );

			pairing_listener = null;
		}

		unloaded = true;
	}

	private void
	setupResources()
	{
		home_page = param_home.getValue().trim();

		if ( home_page.length() == 0 ){

			home_page = null;

		}else if ( !home_page.startsWith("/" )){

			home_page = "/" + home_page;
		}

		resource_root = param_rootres.getValue().trim();

		if ( resource_root.length() == 0 ){

			resource_root = null;

		}else if ( resource_root.startsWith("/" )){

			resource_root = resource_root.substring(1);
		}

		root_dir	= param_rootdir.getValue().trim();

		if ( root_dir.length() == 0 ){

			String pluginDirectoryName = plugin_interface.getPluginDirectoryName();

			file_root = pluginDirectoryName == null
					? FileUtil.newFile(SystemProperties.getUserPath(), "web")
					: FileUtil.newFile(pluginDirectoryName);
		}else{

				// absolute or relative

			if ( root_dir.startsWith(File.separator) || root_dir.contains(":")){

				file_root = FileUtil.newFile(root_dir);

			}else{

				if ( File.separatorChar != '/' && root_dir.contains( "/" )){

					root_dir = root_dir.replace( '/', File.separatorChar );
				}

					// try relative to plugin dir

				String pluginDirectoryName = plugin_interface.getPluginDirectoryName();

				if ( pluginDirectoryName != null ){

					file_root = FileUtil.newFile(pluginDirectoryName, root_dir);

					if ( !file_root.exists()){

						// try relative to plugin classpath
						try {
							String pluginClass = plugin_interface.getPluginProperties().getProperty(
									"plugin.class");
							file_root = FileUtil.newFile(
									Class.forName(
											pluginClass).getProtectionDomain().getCodeSource().getLocation().getPath(),
									root_dir);
							if (!file_root.exists()) {

								file_root = null;
							}
						} catch (Throwable e) {
						}

					}
				}

				if ( file_root == null ){

					file_root = FileUtil.newFile(SystemProperties.getUserPath(), "web", root_dir);
				}
			}
		}


		if ( !file_root.exists()){

			String	error = "WebPlugin: root dir '" + file_root + "' doesn't exist";

			log.log( LoggerChannel.LT_ERROR, error );

		}else if ( !file_root.isDirectory()){

			String	error = "WebPlugin: root dir '" + file_root + "' isn't a directory";

			log.log( LoggerChannel.LT_ERROR, error );
		}

		welcome_files = new File[welcome_pages.length];

		for (int i=0;i<welcome_pages.length;i++){

			welcome_files[i] = FileUtil.newFile( file_root, welcome_pages[i] );
		}
		
		initStage(2);
	}

	private void
	setupAccess()
	{
		String	access_str = param_access.getValue().trim();

		String ip_ranges_str = "";

		ip_ranges 		= null;
		ip_range_all	= false;

		if ( access_str.length() > 7 && Character.isDigit(access_str.charAt(0))){

			String[] ranges = access_str.replace( ';', ',' ).split( "," );

			ip_ranges = new ArrayList<>();

			for ( String range: ranges ){

				range = range.trim();

				if ( range.length() > 7 ){

					IPRange ip_range	= plugin_interface.getIPFilter().createRange(range.contains( ":" )?2:1, true);

					int	sep = range.indexOf("-");

					if ( sep == -1 ){

						ip_range.setStartIP( range );

						ip_range.setEndIP( range );

					}else{

						ip_range.setStartIP( range.substring(0,sep).trim());

						ip_range.setEndIP( range.substring( sep+1 ).trim());
					}

					ip_range.checkValid();

					if (!ip_range.isValid()){

						log.log( LoggerChannel.LT_ERROR, "Access parameter '" + range + "' is invalid" );

					}else{

						ip_ranges.add( ip_range );

						ip_ranges_str += (ip_ranges_str.length()==0?"":", ") + ip_range.getStartIP() + " - " + ip_range.getEndIP();
					}
				}
			}

			if ( ip_ranges.size() == 0 ){

				ip_ranges = null;
			}
		}else{

			if ( access_str.equalsIgnoreCase( "all" ) || access_str.length() == 0 ){

				ip_range_all	= true;
			}
		}

		log.log( 	LoggerChannel.LT_INFORMATION,
				"Acceptable IP range = " +
					( ip_ranges==null?
						(ip_range_all?"all":"local"):
						(ip_ranges_str)));
	}
	
	protected boolean
	verifyReferrer()
	{
		return( true );
	}

	protected void
	setupServer()
	{
		try{
			if ( !plugin_enabled ){

				if ( tracker_context != null ){

					tracker_context.destroy();

					tracker_context = null;
				}

				return;
			}

			int requested_port	= param_port.getValue();

			String protocol_str = param_protocol.getValue().trim();

			String bind_str = param_bind.getValue().trim();

			InetAddress	bind_ip = null;

			if ( bind_str.length() > 0 ){

				try{
					bind_ip = InetAddress.getByName( bind_str );

				}catch( Throwable  e ){
				}

				if ( bind_ip == null ){

						// might be an interface name, see if we can resolve it

					final NetworkAdmin na = NetworkAdmin.getSingleton();

					InetAddress[] addresses = na.resolveBindAddresses( bind_str );

					if ( addresses.length > 0 ){

						bind_ip = addresses[0];

						if ( !na_intf_listener_added ){

							na_intf_listener_added = true;

							na.addPropertyChangeListener(
								new NetworkAdminPropertyChangeListener()
								{
									@Override
									public void
									propertyChanged(
										String property)
									{
										if ( unloaded ){

											na.removePropertyChangeListener( this );

										}else{

											if ( property == NetworkAdmin.PR_NETWORK_INTERFACES ){

												new AEThread2( "setupserver" )
												{
													@Override
													public void
													run()
													{
														setupServer();
													}
												}.start();
											}
										}
									}
								});
						}
					}
				}

				if ( bind_ip == null ){

					log.log( LoggerChannel.LT_ERROR, "Bind IP parameter '" + bind_str + "' is invalid" );
				}
			}

			if ( tracker_context != null ){

				URL	url = tracker_context.getURLs()[0];

				String		existing_protocol 	= url.getProtocol();
				int			existing_port		= url.getPort()==-1?url.getDefaultPort():url.getPort();
				InetAddress existing_bind_ip 	= tracker_context.getBindIP();

				if ( 	( existing_port == requested_port || requested_port == 0 ) &&
						existing_protocol.equalsIgnoreCase( protocol_str ) &&
						sameAddress( bind_ip, existing_bind_ip )){

					return;
				}

				tracker_context.destroy();

				tracker_context = null;
			}

			int	protocol = protocol_str.equalsIgnoreCase( "HTTP")?Tracker.PR_HTTP:Tracker.PR_HTTPS;

			Map<String,Object>		tc_properties = new HashMap<>();

			Boolean prop_non_blocking = (Boolean)properties.get( PR_NON_BLOCKING );

			if ( prop_non_blocking != null && prop_non_blocking ){

				tc_properties.put( Tracker.PR_NON_BLOCKING, true );
			}

			log.log( 	LoggerChannel.LT_INFORMATION,
						"Server initialisation: port=" + requested_port +
						(bind_ip == null?"":(", bind=" + bind_str + "->" + bind_ip + ")")) +
						", protocol=" + protocol_str +
						(root_dir.length()==0?"":(", root=" + root_dir )) +
						(properties.size()==0?"":(", props=" + properties )));

			tracker_context =
				plugin_interface.getTracker().createWebContext(
						Constants.APP_NAME + " - " + plugin_interface.getPluginName(),
						requested_port, protocol, bind_ip, tc_properties );

			int server_port = getServerPort();
			
			Boolean prop_enable_i2p = (Boolean)properties.get( PR_ENABLE_I2P );

			if ( prop_enable_i2p == null || prop_enable_i2p ){

				network_dispatcher.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							Map<String,Object>	options = new HashMap<>();

							options.put( AEProxyFactory.SP_PORT, server_port );

							InetAddress bind = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();
							
							if ( bind != null && !bind.isAnyLocalAddress()){
								
								options.put( AEProxyFactory.SP_BIND, bind.getHostAddress());
							}

							Map<String,Object> reply =
									AEProxyFactory.getPluginServerProxy(
										plugin_interface.getPluginName(),
										AENetworkClassifier.AT_I2P,
										plugin_interface.getPluginID(),
										options );

							if ( reply != null ){

								param_i2p_dest.setVisible( true );

								String host = (String)reply.get( "host" );

								if ( !param_i2p_dest.getValue().equals( host )){

									param_i2p_dest.setValue( host );

									if ( p_sid != null ){

										updatePairing( p_sid );
									}
								}
							}
						}
					});
			}

			Boolean prop_enable_tor = (Boolean)properties.get( PR_ENABLE_TOR );

			if ( prop_enable_tor == null || prop_enable_tor ){

				network_dispatcher.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							Map<String,Object>	options = new HashMap<>();

							options.put( AEProxyFactory.SP_PORT, server_port );

							InetAddress bind = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();
							
							if ( bind != null && !bind.isAnyLocalAddress()){
								
								options.put( AEProxyFactory.SP_BIND, bind.getHostAddress());
							}

							Map<String,Object> reply =
									AEProxyFactory.getPluginServerProxy(
										plugin_interface.getPluginName(),
										AENetworkClassifier.AT_TOR,
										plugin_interface.getPluginID(),
										options );

							if ( reply != null ){

								param_tor_dest.setVisible( true );

								String host = (String)reply.get( "host" );

								if ( !param_tor_dest.getValue().equals( host )){

									param_tor_dest.setValue( host );

									if ( p_sid != null ){

										updatePairing( p_sid );
									}
								}
							}
						}
					});
			}


			Boolean	pr_enable_keep_alive = (Boolean)properties.get( PR_ENABLE_KEEP_ALIVE );

			if ( pr_enable_keep_alive != null && pr_enable_keep_alive ){

				tracker_context.setEnableKeepAlive( true );
			}

			tracker_context.addPageGenerator( this );

			tracker_context.addAuthenticationListener(
				new TrackerAuthenticationAdapter()
				{
					private String	last_pw		= "";
					private byte[]	last_hash	= {};

					private final int DELAY = 10*1000;

					private Map<String,Object[]>	fail_map = new HashMap<>();

					@Override
					public boolean
					authenticate(
						String		headers,
						URL			resource,
						String		user,
						String		pw )
					{
						//System.out.println( resource + ": " + user + "/" + pw );

						long	now = SystemTime.getMonotonousTime();

						String	client_address = getHeaderField( headers, "X-Real-IP" );

						if ( client_address == null ){

							client_address = "<unknown>";
						}

						synchronized( logout_timer ){

							Long logout_time = logout_timer.get( client_address );

							if ( logout_time != null && now - logout_time <= LOGOUT_GRACE_MILLIS ){

								tls.set( GRACE_PERIOD_MARKER );

								return( true );
							}
						}

						boolean	result = authenticateSupport( client_address, headers, resource, user, pw );

						if ( !result ){

								// don't delay clients that keep failing to send auth entirely (old Android browsers for example)

							if ( !pw.equals( "" )){

								AESemaphore waiter = null;

								synchronized( fail_map ){


									Object[] x = fail_map.get( client_address );

									if ( x == null ){

										x = new Object[]{ new AESemaphore( "af:waiter" ), new Long(-1), new Long(-1), now };

										fail_map.put( client_address, x );

									}else{

										x[1] = x[2];
										x[2] = x[3];
										x[3] = now;

										long t = (Long)x[1];

										if ( now - t < 10*1000 ){

											log( "Too many recent authentication failures from '" + client_address + "' - rate limiting" );

											x[2] = now+DELAY;
											// there's a bug where flipping the password on doesn't reset the pw so we automatically fail without checking
											// this is not the correct fix, but it works
											last_pw = "";
											waiter = (AESemaphore)x[0];
										}
									}
								}

								if ( waiter != null ){

									waiter.reserve( DELAY );
								}
							}
						} else {
							// Some clients have no cookie support and will always try with
							// no auth info, then, once getting a failed response, try again
							// with the auth info.
							// This results in a loop of 1 good, 1 bad.
							// Prevent this from causing the "too many recent failures" delay to kick in by removing from map
							// on goodness

							synchronized( fail_map ){

								fail_map.remove( client_address );
							}

							String	cookies = getHeaderField( headers, "Cookie" );

							if ( pairing_session_code != null ){

								if ( cookies == null || !cookies.contains( pairing_session_code )){

									tls.set( pairing_session_code );
								}
							}
						}

						recordAuthRequest( client_address, result );

						if ( !result ){

								// going to be generous here as (old android browsers at least) sometimes fail to provide
								// auth on .png files

								// no I'm not, too many risks associated with this (e.g. xmwebui has some
								// prefix url logic which may be exploitable)

							//if ( resource.getPath().endsWith( ".png" )){
							//
							//	result = true;
							//}
						}

						return( result );
					}

					private boolean
					authenticateSupport(
						String		client_address,
						String		headers,
						URL			resource,
						String		user,
						String		pw )
					{
						boolean	result;

						boolean	auto_auth =  param_auto_auth != null && param_auto_auth.getValue();

						if ( !pw_enable.getValue()){

							String whitelist = p_no_pw_whitelist.getValue().trim();
							
							if ( whitelist.equals( "*" )){
							
								result = true;
								
							}else{
																
								int		this_server_port = protocol == Tracker.PR_HTTP?80:443;
								
								String 	referrer = getHeaderField( headers, "referer" );
								
								String 	host_maybe_null = getHeaderField( headers, "host" );

								if ( host_maybe_null != null ){
									
									if ( host_maybe_null.startsWith( "[" )){
										
										int	pos = host_maybe_null.lastIndexOf( ']' );
										
										if ( pos != -1 ){
											
											String rem = host_maybe_null.substring( pos+1 );
											
											host_maybe_null = host_maybe_null.substring( 0, pos+1 );
											
											pos = rem.indexOf( ':' );
											
											if ( pos != -1 ){
												
												this_server_port = Integer.parseInt( rem.substring( pos+1 ).trim());
											}
										}
									}else{
										
										int pos = host_maybe_null.indexOf( ':' );
										
										if ( pos != -1 ){
											
											this_server_port = Integer.parseInt( host_maybe_null.substring( pos+1 ).trim());
											
											host_maybe_null = host_maybe_null.substring( 0,  pos );
										}
									}
								}
																
								result = false;
								
								String msg	= "";
								
								if ( this_server_port != server_port ){
									
									msg = "port mismatch: " + server_port + "/" + this_server_port;
									
								}else{
								
									String[] allowed = whitelist.split( "," );

									for ( String a: allowed ){
									
										a = a.trim();
										
										if ( a.equals( "*" )){
											
											result = true;
											
											break;
											
										}else if ( a.equals( "$" )){
										
											InetAddress bind = getServerBindIP();
											
											if ( bind != null ){
												
												if ( bind instanceof Inet6Address ){
													
													a = "[" + bind.getHostAddress() + "]";
													
												}else{
													
													a = bind.getHostAddress();
												}
											}
										}
										
										if ( client_address.equals( a.trim())){
											
											result = true;
											
											break;
										}

										String aTrimmed = a.trim();

											// Support ranges (copied from code in setupAccess)
										
										IPRange ip_range	= plugin_interface.getIPFilter().createRange(aTrimmed.contains( ":" )?2:1, true);
										
										int	sep = aTrimmed.indexOf("-");

										if ( sep == -1 ){

											ip_range.setStartIP( aTrimmed );

											ip_range.setEndIP( aTrimmed );

										}else{

											ip_range.setStartIP( aTrimmed.substring(0,sep).trim());

											ip_range.setEndIP( aTrimmed.substring( sep+1 ).trim());
										}

										ip_range.checkValid();

										if (ip_range.isValid() && ip_range.isInRange(client_address)){

											result = true;

											break;
										}

									}
									
									if ( !result ){
										
										msg = "host '" + client_address + "' not in whitelist";
										
									}else{
										
										if ( referrer != null && verifyReferrer()){
											
											result = false;
											
											try{
												
												URL url = new URL( referrer );
												
												int ref_port = url.getPort();
												
												if ( ref_port == -1 ){
													
													ref_port = url.getDefaultPort();
												}
												
												if ( ref_port == server_port ){
														
													result = true;
												}
											}catch( Throwable e ){
											}
											
											if ( !result ){
												
												msg = "referrer mismatch: " + referrer;
											}
										}
									}
								}
								
								if ( !result ){
									
									
									log.log( "Access denied: No password and " + msg + " (" + client_address + ")");
								}
							}
						}else{

							if ( auto_auth ){

								user = user.trim().toLowerCase();

								pw = pw.toUpperCase();
							}

							if ( !user.equals( p_user_name.getValue())){

								log.log( "Access denied: Incorrect user name: " + user + " (" + client_address + ")" );
								
								result = false;

							}else{

								byte[]	hash = last_hash;

								if (  !last_pw.equals( pw )){

									hash = plugin_interface.getUtilities().getSecurityManager().calculateSHA1(
											auto_auth?pw.toUpperCase().getBytes():pw.getBytes());

									last_pw		= pw;
									last_hash	= hash;
								}

								result = Arrays.equals( hash, p_password.getValue());
								
								if ( !result ){
									
									log.log( "Access denied: Incorrect password" + " (" + client_address + ")" );
								}
							}
						}

						if ( result ){

								// user name and password match, see if we've come from the pairing process

							checkCookieSet( headers, resource );

						}else if ( auto_auth  ){

								// either the ac is in the url, referer or we have a cookie set

							int x = checkCookieSet( headers, resource );

							if ( x == 1 ){

								result = true;

							}else if ( x == 0 ){

								result = hasOurCookie( getHeaderField( headers, "Cookie" ));
							}
						}else{

							result = hasOurCookie( getHeaderField( headers, "Cookie" ));
						}

						return( result );
					}

						/**
						 *
						 * @param headers
						 * @param resource
						 * @return 0 = unknown, 1 = ok, 2 = bad
						 */

					private int
					checkCookieSet(
						String		headers,
						URL			resource )
					{
						if ( pairing_access_code == null ){

							return( 2 );
						}

						String[]	locations = { resource.getQuery(), getHeaderField( headers, "Referer" )};

						for ( String location: locations ){

							if ( location != null ){

								boolean	skip_fail 	= false;
								int		param_len	= 0;

								int p1 = location.indexOf( "vuze_pairing_ac=" );

								if ( p1 == -1 ){

									p1 = location.indexOf( "ac=" );

									if ( p1 != -1 ){

										param_len = 3;

										skip_fail = true;
									}
								}else{

									param_len = 16;
								}

								if ( p1 != -1 ){

									int p2 = location.indexOf( '&', p1 );

									String ac = location.substring( p1+param_len, p2==-1?location.length():p2 ).trim();

									p2 = ac.indexOf( '#' );

									if ( p2 != -1 ){

										ac = ac.substring( 0, p2 );
									}

									if ( ac.equalsIgnoreCase( pairing_access_code )){

										tls.set( pairing_session_code );

										return( 1 );

									}else{

										if ( !skip_fail ){

											return( 2 );
										}
									}
								}
							}
						}

						return( 0 );
					}

					private String
					getHeaderField(
						String	headers,
						String	field )
					{
						String[] lines = headers.split( "\n" );
						
						for ( String line: lines ){
							
							int	pos = line.indexOf( ':' );
							
							if ( pos != -1 ){
								
								if ( line.substring( 0, pos ).equalsIgnoreCase( field )){
									
									return( line.substring( pos+1 ).trim());
								}
							}
						}
						
						return( null );
					}
				});

		}catch( TrackerException e ){

			log.log( "Server initialisation failed", e );
		}
	}

	private boolean
	hasOurCookie(
		String		cookies )
	{
		if ( cookies == null ){

			return( false );
		}

		String[] cookie_list = cookies.split( ";" );

		for ( String cookie: cookie_list ){

			String[] bits = cookie.split( "=" );

			if ( bits.length == 2 ){

				if ( bits[0].trim().equals( "vuze_pairing_sc" )){

					if ( bits[1].trim().equals( pairing_session_code )){

						return( true );
					}
				}
			}
		}

		return( false );
	}

	private boolean
	sameAddress(
		InetAddress	a1,
		InetAddress a2 )
	{
		if ( a1 == null && a2 == null ){

			return( true );

		}else if ( a1 == null || a2 == null ){

			return( false );

		}else{

			return( a1.equals( a2 ));
		}
	}

	protected void
	setupUPnP()
	{
		if ( !plugin_enabled  || !p_upnp_enable.getValue()){

			if ( upnp_mapping != null ){

				log( "Removing UPnP mapping" );

				upnp_mapping.destroy();

				upnp_mapping = null;
			}

			return;
		}

		PluginInterface pi_upnp = plugin_interface.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );

		if ( pi_upnp == null ){

			log.log( "No UPnP plugin available, not attempting port mapping");

		}else{

			int port = param_port.getValue();

			if ( upnp_mapping != null ){

				if ( upnp_mapping.getPort() == port ){

					return;
				}

				log( "Updating UPnP mapping" );

				upnp_mapping.destroy();

			}else{

				log( "Creating UPnP mapping" );
			}

			upnp_mapping = ((UPnPPlugin)pi_upnp.getPlugin()).addMapping( plugin_interface.getPluginName(), true, port, true );
		}
	}

	protected void
	setupPairing(
		String		sid,
		boolean		pairing_enabled )
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		PairedService service = pm.getService( sid );

		if ( plugin_enabled && pairing_enabled && pm.isEnabled()){

			setupAutoAuth();

			if ( service == null ){

				log( "Adding pairing service" );

				service =
					pm.addService(
						sid,
						new PairedServiceRequestHandler()
						{
							@Override
							public byte[]
							handleRequest(
								InetAddress originator,
								String		endpoint_url,
								byte[] 		request )

								throws IOException
							{
								return( handleTunnelRequest( originator, endpoint_url, request ));
							}
						});

				PairingConnectionData cd = service.getConnectionData();

				try{
					updatePairing( cd );

				}finally{

					cd.sync();
				}
			}
		}else{

			pairing_access_code 	= null;

			setupSessionCode( null );

			if ( service != null ){

				log( "Removing pairing service" );

				service.remove();
			}
		}
	}

	private void
	setupSessionCode(
		String		key )
	{
		if ( key == null ){

			key = Base32.encode( p_user_name.getValue().getBytes()) + Base32.encode( p_password.getValue());
		}

		synchronized( this ){

			String existing_key = plugin_config.getPluginStringParameter( PAIRING_SESSION_KEY, "" );

			String[]	bits = existing_key.split( "=" );

			if ( bits.length == 2 && bits[0].equals( key )){

				pairing_session_code = bits[1];

			}else{

				pairing_session_code = Base32.encode( RandomUtils.nextSecureHash());

				plugin_config.setPluginParameter( PAIRING_SESSION_KEY, key + "=" + pairing_session_code );
			}
		}
	}

	protected void
	setupAutoAuth()
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		String ac = pm.peekAccessCode();

		pairing_access_code = ac;

			// good time to check the default pairing auth settings

		if ( pairing_access_code != null && param_auto_auth.getValue()){

			setupSessionCode( ac );

			try{
				setting_auto_auth = true;

				if ( !p_user_name.getValue().equals( "vuze" )){

					p_user_name.setValue( "vuze" );
				}

		        SHA1Hasher hasher = new SHA1Hasher();

		        byte[] encoded = hasher.calculateHash( pairing_access_code.getBytes());

				if ( !Arrays.equals( p_password.getValue(), encoded )){

					p_password.setValue( pairing_access_code );
				}

				if ( !pw_enable.getValue()){

					pw_enable.setValue( true );
				}
			}finally{

				setting_auto_auth = false;
			}
		}else{

			setupSessionCode( null );
		}
	}

	protected void
	updatePairing(
		String		sid )
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		PairedService service = pm.getService( sid );

		if ( service != null ){

			PairingConnectionData cd = service.getConnectionData();

			log( "Updating pairing information" );

			try{
				updatePairing( cd );

			}finally{

				cd.sync();
			}
		}
	}

	protected void
	updatePairing(
		PairingConnectionData		cd )
	{
		cd.setAttribute( PairingConnectionData.ATTR_PORT, 		String.valueOf( param_port.getValue()));

		int	override = param_port_or==null?0:param_port_or.getValue();

		if ( override > 0 ){

			cd.setAttribute( PairingConnectionData.ATTR_PORT_OVERRIDE, 	String.valueOf( override ));

		}else{

			cd.setAttribute( PairingConnectionData.ATTR_PORT_OVERRIDE, null );
		}

		cd.setAttribute( PairingConnectionData.ATTR_PROTOCOL, 	param_protocol.getValue());

		if ( param_i2p_dest.isVisible()){

			String host = param_i2p_dest.getValue();

			if ( host.length() > 0 ){

				cd.setAttribute( PairingConnectionData.ATTR_I2P, host );
			}
		}

		if ( param_tor_dest.isVisible()){

			String host = param_tor_dest.getValue();

			if ( host.length() > 0 ){

				cd.setAttribute( PairingConnectionData.ATTR_TOR, host );
			}
		}
	}

	public InetAddress
	getServerBindIP()
	{
		if ( tracker_context == null ){

			return( new InetSocketAddress(0).getAddress());
		}

		InetAddress address = tracker_context.getBindIP();

		if ( address == null ){

			return( new InetSocketAddress(0).getAddress());
		}

		return( address );
	}

	public int
	getServerPort()
	{
		if ( tracker_context == null ){

			return( 0 );
		}

		URL	url = tracker_context.getURLs()[0];

		return( url.getPort()==-1?url.getDefaultPort():url.getPort());
	}

	protected String
	getServerURL()
	{
		InetAddress bind_ip = getServerBindIP();

		InetAddress address;
		
		if ( bind_ip.isAnyLocalAddress()){

			address = NetworkAdmin.getSingleton().getLoopbackAddress();
			
		}else{

			address = bind_ip;
		}
		
		return( getProtocol().toLowerCase( Locale.US ) + "://" + UrlUtils.getURLForm( address, getPort()) + "/" );
	}
	
	public int
	getPort()
	{
		return( param_port.getValue());
	}

	public String
	getProtocol()
	{
		return( param_protocol.getValue());
	}

	public void
	setUserAndPassword(
		String		user,
		String		password )
	{
		p_user_name.setValue( user );
		p_password.setValue( password );
		pw_enable.setValue( true );
	}

	public void
	unsetUserAndPassword()
	{
		pw_enable.setValue( false );
	}

	private void
	recordAuthRequest(
		String						client_ip,
		boolean						good )
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		pm.recordRequest( plugin_interface.getPluginName(), client_ip, good );
	}

	private void
	recordRequest(
		TrackerWebPageRequest		request,
		boolean						good,
		boolean						is_tunnel )
	{
		PairingManager pm = PairingManagerFactory.getSingleton();

		String	str = request.getClientAddress();

		if ( is_tunnel ){

			str = "Tunnel (" + str + ")";
		}

		pm.recordRequest( plugin_interface.getPluginName(), str, good );
	}

	public boolean
	generateSupport(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		return( false );
	}

	private byte[]
	handleTunnelRequest(
		final InetAddress		originator,
		String					endpoint_url,
		final byte[]			request_bytes )

		throws IOException
	{
		int	q_pos = endpoint_url.indexOf( '?' );

		boolean	raw = true;

		if ( q_pos != -1 ){

			String params = endpoint_url.substring( q_pos+1 );

			String[] args = params.split( "&" );

			String new_endpoint = endpoint_url.substring( 0, q_pos );

			String	sep = "?";

			for ( String arg: args ){

				if ( arg.startsWith( "tunnel_format=" )){

					String temp = arg.substring( 14 );

					if ( temp.startsWith( "h" )){

						raw = false;
					}
				}else{

					new_endpoint += sep + arg;

					sep = "&";
				}
			}

			endpoint_url = new_endpoint;
		}

		final String		f_endpoint_url	= endpoint_url;
		final JSONObject	request_headers = new JSONObject();

		final int			data_start;

		if ( raw ){

			data_start = 0;

		}else{
			int	request_header_len = ((request_bytes[0]<<8)&0x0000ff00) | (request_bytes[1]&0x000000ff);

			String	reply_json_str = new String( request_bytes, 2, request_header_len, "UTF-8" );

			request_headers.putAll( JSONUtils.decodeJSON( reply_json_str ));

			data_start = request_header_len + 2;
		}

		TrackerWebPageRequest request =
			new TrackerWebPageRequest()
			{
				@Override
				public Tracker
				getTracker()
				{
					return( null );
				}

				@Override
				public String
				getClientAddress()
				{
					return( originator.getHostAddress());
				}

				@Override
				public InetSocketAddress
				getClientAddress2()
				{
					return( new InetSocketAddress( originator, 0 ));
				}

				@Override
				public InetSocketAddress
				getLocalAddress()
				{
					return( new InetSocketAddress( "127.0.0.1", 0 ));
				}

				@Override
				public String
				getUser()
				{
					return( null );
				}

				@Override
				public String
				getURL()
				{
					String url = (String)request_headers.get( "HTTP-URL" );

					if ( url != null ){

						return( url );
					}

					return( f_endpoint_url );
				}

				@Override
				public String
				getHeader()
				{
					return( "" );
				}

				@Override
				public Map
				getHeaders()
				{
					return( request_headers );
				}

				@Override
				public InputStream
				getInputStream()
				{
					return( new ByteArrayInputStream( request_bytes, data_start, request_bytes.length - data_start ));
				}

				@Override
				public URL
				getAbsoluteURL()
				{
					try{
						return( new URL( "http://127.0.0.1" + getURL()));

					}catch( Throwable e ){

						return( null );
					}
				}

				@Override
				public TrackerWebContext
				getContext()
				{
					return( null );
				}
			};
		final ByteArrayOutputStream[]	baos = { new ByteArrayOutputStream()};

		final Map	reply_headers	= new HashMap();

		TrackerWebPageResponse	response =
			new TrackerWebPageResponse()
			{
				@Override
				public OutputStream
				getOutputStream()
				{
					return( baos[0] );
				}

				@Override
				public void 
				setOutputStream(
					ByteArrayOutputStream os)
				{	
					baos[0] = os;
				}
				
				@Override
				public void
				setReplyStatus(
					int		status )
				{
					reply_headers.put( "HTTP-Status", String.valueOf( status ));
				}

				@Override
				public void
				setContentType(
					String		type )
				{
					reply_headers.put( "Content-Type", type );
				}

				public String
				getContentType()
				{
					return( (String)reply_headers.get( "Content-Type" ));
				}
				
				@Override
				public void
				setLastModified(
					long		time )
				{
				}

				@Override
				public void
				setExpires(
					long		time )
				{
				}

				@Override
				public void
				setHeader(
					String		name,
					String		value )
				{
					reply_headers.put( name, value );
				}

				@Override
				public void
				setGZIP(
					boolean		gzip )
				{
				}

				@Override
				public boolean
				useFile(
					String		root_dir,
					String		relative_url )

					throws IOException
				{
					Debug.out( "Not supported" );

					return( false );
				}

				@Override
				public void
				useStream(
					String		file_type,
					InputStream	stream )

					throws IOException
				{
					Debug.out( "Not supported" );
				}

				@Override
				public void
				writeTorrent(
					TrackerTorrent	torrent )

					throws IOException
				{
					Debug.out( "Not supported" );
				}

				@Override
				public void
				setAsynchronous(
					boolean		async )

					throws IOException
				{
					Debug.out( "Not supported" );
				}

				@Override
				public boolean
				getAsynchronous()
				{
					return( false );
				}

				@Override
				public OutputStream
				getRawOutputStream()

					throws IOException
				{
					Debug.out( "Not supported" );

					throw( new IOException( "Not supported" ));
				}

				@Override
				public boolean
				isActive()
				{
					return( true );
				}
			};

		try{
			byte[]		bytes;

			if ( generate2( request, response, true )){

				bytes = baos[0].toByteArray();

			}else{

				Debug.out( "Tunnelled request not handled: " + request.getURL());

				response.setReplyStatus( 404 );

				bytes = new byte[0];
			}

			if ( raw ){

				return( bytes );

			}else{

				String accept_encoding = (String)request_headers.get( "Accept-Encoding" );

				if ( accept_encoding != null && accept_encoding.contains( "gzip" )){

					reply_headers.put( "Content-Encoding", "gzip" );

					ByteArrayOutputStream	temp = new ByteArrayOutputStream( bytes.length + 512 );

					GZIPOutputStream gos = new GZIPOutputStream( temp );

					gos.write( bytes );

					gos.close();

					bytes = temp.toByteArray();
				}

				ByteArrayOutputStream baos2 = new ByteArrayOutputStream( bytes.length + 512 );

				String header_json = JSONUtils.encodeToJSON( reply_headers );

				byte[] header_bytes = header_json.getBytes( "UTF-8" );

				int	header_len = header_bytes.length;

				byte[] header_len_bytes = new byte[]{ (byte)(header_len>>8), (byte)header_len };

				baos2.write( header_len_bytes );
				baos2.write( header_bytes );
				baos2.write( bytes );

				return( baos2.toByteArray());
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( new byte[0] );
		}
	}

	@Override
	public boolean
	generate(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		String url = request.getURL();

		if ( url.startsWith( "/pairing/tunnel/" )){

			long	error_code = 1;

			try{
				final PairingManager pm = PairingManagerFactory.getSingleton();

				if ( pm.isEnabled()){

					if ( pm.isSRPEnabled()){

						return( pm.handleLocalTunnel( request, response ));

					}else{

						error_code = 5;

						throw( new IOException( "Secure pairing is not enabled" ));
					}
				}else{

					error_code = 5;

					throw( new IOException( "Pairing is not enabled" ));
				}
			}catch( Throwable e ){

				JSONObject json = new JSONObject();

				JSONObject error = new JSONObject();

				json.put( "error", error );

				error.put( "msg", Debug.getNestedExceptionMessage(e));
				error.put( "code", error_code );

				return( returnJSON( response, JSONUtils.encodeToJSON( json )));
			}
		}

		return( generate2( request, response, false ));
	}

	private boolean
	generate2(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response,
		boolean						is_tunnel )

		throws IOException
	{
		// System.out.println( request.getURL());

		String	client = request.getClientAddress();

		if ( !ip_range_all ){

			// System.out.println( "client = " + client );

			try{
				boolean valid_ip = true;

				InetAddress client_ia = InetAddress.getByName( client );

				if ( ip_ranges == null ){

					if ( !client_ia.isLoopbackAddress()){

						InetAddress bind_ia = getServerBindIP();

						if ( bind_ia.isAnyLocalAddress() || !bind_ia.equals( client_ia )){

							log.log( LoggerChannel.LT_ERROR, "Client '" + client + "' is not local, rejecting" );

							valid_ip = false;
						}
					}
				}else{

					boolean ok = false;

					for ( IPRange range: ip_ranges ){

						if ( range.isInRange( client_ia.getHostAddress())){

							ok = true;
						}
					}

					if ( !ok ){

						log.log( LoggerChannel.LT_ERROR, "Client '" + client + "' (" + client_ia.getHostAddress() + ") is not in range, rejecting" );

						valid_ip = false;
					}
				}

				if ( !valid_ip ){

					response.setReplyStatus( 403 );

					recordRequest( request, false, is_tunnel );

					return( returnTextPlain( response, "Cannot access resource from this IP address." ));
				}

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				recordRequest( request, false, is_tunnel );

				return( false );
			}
		}

		recordRequest( request, true, is_tunnel );

		String url = request.getURL();

		if ( url.toString().endsWith(".class")){

			System.out.println( "WebPlugin::generate:" + url );
		}

		String	cookie_to_set = tls.get();

		if ( cookie_to_set == GRACE_PERIOD_MARKER ){

			return( returnTextPlain( response, "Logout in progress, please try again later." ));
		}

		if ( cookie_to_set != null ){

				// set session cookie

			response.setHeader( "Set-Cookie", "vuze_pairing_sc=" + cookie_to_set + "; path=/; HttpOnly" );

			tls.set( null );
		}

		URL full_url = request.getAbsoluteURL();

		String	full_url_path = full_url.getPath();

		if ( full_url_path.equals( "/isPairedServiceAvailable" )){

			String redirect = getArgumentFromURL( full_url, "redirect_to" );

			if ( redirect != null ){

				try{
					URL target = new URL( redirect );

					String	host = target.getHost();

					if ( !Constants.isAppDomain( host )){

						if ( !InetAddress.getByName(host).isLoopbackAddress()){

							log( "Invalid redirect host: " + host );

							redirect = null;
						}
					}
				}catch( Throwable e ){

					Debug.out( e );

					redirect = null;
				}
			}

			if ( redirect != null ){

				response.setReplyStatus( 302 );

				response.setHeader( "Location", redirect );

				return( true );
			}

			String callback = getArgumentFromURL( full_url, "jsoncallback" );

			if ( callback != null ){

				return( returnTextPlain( response,  callback + "( {'pairedserviceavailable':true} )"));
			}
		}else if ( full_url_path.equals( "/isServicePaired" )){

			boolean paired = cookie_to_set != null || hasOurCookie((String)request.getHeaders().get( "cookie" ));

				// DON'T use returnJSON here as it DOESN'T work in the web ui for some reason!

			return( returnTextPlain( response, "{ 'servicepaired': " + ( paired?"true":"false" ) + " }" ));

		}else if ( full_url_path.equals( "/pairedServiceLogout")){

			synchronized( logout_timer ){

				logout_timer.put( client, SystemTime.getMonotonousTime());
			}

			response.setHeader( "Set-Cookie", "vuze_pairing_sc=<deleted>, expires=" + TimeFormatter.getCookieDate(0));

			String redirect = getArgumentFromURL( full_url, "redirect_to" );

			if ( redirect != null ){

				try{
					URL target = new URL( redirect );

					String	host = target.getHost();

					if ( !Constants.isAppDomain( host )){

						if ( !InetAddress.getByName(host).isLoopbackAddress()){

							log( "Invalid redirect host: " + host );

							redirect = null;
						}
					}
				}catch( Throwable e ){

					Debug.out( e );

					redirect = null;
				}
			}
			if ( redirect == null ){

				return( returnTextPlain( response, "" ));

			}else{

				response.setReplyStatus( 302 );

				response.setHeader( "Location", redirect );

				return( true );
			}
		}

		request.getHeaders().put( "x-vuze-is-tunnel", is_tunnel?"true":"false" );

		if ( generateSupport( request, response )){

			return(true);
		}

		if ( is_tunnel ){

			return( false );
		}

		if ( url.equals("/") || url.startsWith( "/?" )){

			url = "/";

			if ( home_page != null ){

				url = home_page;

			}else{

				for (int i=0;i<welcome_files.length;i++){

					if ( welcome_files[i].exists()){

						url = "/" + welcome_pages[i];

						break;
					}
				}
			}
		}

			// first try file system for data

		if ( useFile( request, response, file_root, UrlUtils.decode( url ))){

			return( true );
		}

				// now try jars

		String	resource_name = url;

		if (resource_name.startsWith("/")){

			resource_name = resource_name.substring(1);
		}

		int	pos = resource_name.lastIndexOf(".");

		if ( pos != -1 ){

			String	type = resource_name.substring( pos+1 );

			ClassLoader	cl = plugin_interface.getPluginClassLoader();

			InputStream is = cl.getResourceAsStream( resource_name );

			if ( is == null ){

				// failed absolute load, try relative

				if ( resource_root != null ){

					resource_name = resource_root + "/" + resource_name;

					is = cl.getResourceAsStream( resource_name );
				}
			}

			// System.out.println( resource_name + "->" + is + ", url = " + url );

			if (is != null ){

				try{
					response.useStream( type, is );

				}finally{

					is.close();
				}

				return( true );
			}
		}

		return( false );
	}

		/**
		 * this method can be over-ridden to handle custom file delivery
		 * @param request
		 * @param response
		 * @param root
		 * @param relative_url
		 * @return
		 * @throws IOException
		 */

	protected boolean
	useFile(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response,
		File						root,
		String						relative_url )

		throws IOException
	{
		return( response.useFile( root.getAbsolutePath(), relative_url ));
	}

	private String
	getArgumentFromURL(
		URL			url,
		String		argument )
	{
		String query = url.getQuery();

		if ( query != null ){

			String[] args = query.split( "&" );

			for ( String arg: args ){

				String [] x = arg.split( "=" );

				if ( x.length == 2 ){

					if ( x[0].equals( argument )){

						return( UrlUtils.decode( x[1] ));
					}
				}
			}
		}

		return( null );
	}

	private boolean
	returnTextPlain(
		TrackerWebPageResponse		response,
		String						str )
	{
		return( returnStuff( response, "text/plain", str ));
	}

	private boolean
	returnJSON(
		TrackerWebPageResponse		response,
		String						str )

		throws IOException
	{
		response.setContentType( "application/json; charset=UTF-8" );

		OutputStream os = response.getOutputStream();

		os.write( str.getBytes( "UTF-8" ));

		return( true );
	}

	private boolean
	returnStuff(
		TrackerWebPageResponse		response,
		String						content_type,
		String						str )
	{
		response.setContentType( content_type );

		PrintWriter pw = new PrintWriter( response.getOutputStream());

		pw.println( str );

		pw.flush();

		pw.close();

		return( true );
	}

	protected BasicPluginConfigModel
	getConfigModel()
	{
		return( config_model );
	}

	protected BasicPluginViewModel getViewModel() {
		return this.view_model;
	}

	protected void
	log(
		String	str )
	{
		log.log( str );
	}

	protected void
	log(
		String		str,
		Throwable 	e )
	{
		log.log( str, e );
	}
}
