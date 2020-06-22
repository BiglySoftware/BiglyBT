/*
 * File    : PluginConfigImpl.java
 * Created : 10 nov. 2003
 * By      : epall
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

package com.biglybt.pifimpl.local;

import java.io.File;
import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.net.magneturi.MagnetURIHandler;
import com.biglybt.pifimpl.local.config.ConfigParameterImpl;
import com.biglybt.pifimpl.local.config.PluginConfigSourceImpl;

import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginConfigListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.PluginConfigSource;

public class
PluginConfigImpl
	implements PluginConfig
{

	protected static Map<String,String>	external_to_internal_key_map = new HashMap<>();
	private PluginConfigSourceImpl external_source = null;

	static{

		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC, 		CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC, 		"Max Upload Speed Seeding KBs" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC, 	CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_CONNECTIONS_GLOBAL, 				"Max.Peer.Connections.Total" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_CONNECTIONS_PER_TORRENT, 			"Max.Peer.Connections.Per.Torrent" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_DOWNLOADS, 						"max downloads" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_ACTIVE, 							"max active torrents" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_ACTIVE_SEEDING, 							"StartStopManager_iMaxActiveTorrentsWhenSeeding" );
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_UPLOADS, "Max Uploads");
		external_to_internal_key_map.put( CORE_PARAM_INT_MAX_UPLOADS_SEEDING, "Max Uploads Seeding");
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_MAX_UPLOAD_SPEED_SEEDING, "enable.seedingonly.upload.rate");
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_MAX_ACTIVE_SEEDING, "StartStopManager_bMaxActiveTorrentsWhenSeedingEnabled");
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_AUTO_SPEED_ON, CORE_PARAM_BOOLEAN_AUTO_SPEED_ON);
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON, CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON );
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_SOCKS_PROXY_NO_INWARD_CONNECTION, 	"Proxy.Data.SOCKS.inform" );
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_NEW_SEEDS_START_AT_TOP, 			CORE_PARAM_BOOLEAN_NEW_SEEDS_START_AT_TOP );
		external_to_internal_key_map.put( CORE_PARAM_STRING_LOCAL_BIND_IP, 						"Bind IP" );
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_FRIENDLY_HASH_CHECKING, 			"diskmanager.friendly.hashchecking" );
		external_to_internal_key_map.put( GUI_PARAM_INT_SWT_REFRESH_IN_MS,                      "GUI Refresh");
		external_to_internal_key_map.put( CORE_PARAM_BOOLEAN_NEW_TORRENTS_START_AS_STOPPED,     "Default Start Torrents Stopped");
		external_to_internal_key_map.put( CORE_PARAM_INT_INCOMING_TCP_PORT, "TCP.Listen.Port");
		external_to_internal_key_map.put( CORE_PARAM_INT_INCOMING_UDP_PORT, "UDP.Listen.Port");
		external_to_internal_key_map.put( CORE_PARAM_STRING_DEFAULT_SAVE_PATH, "Default save path");

		// Note: Not in PluginConfig.java because it's an UI option and
		//       not applicable to all UIs
		// TODO: Add a smarter way

		// Following parameters can be set directly (we don't have an alias for these values).
		String[] passthrough_params = new String[] {
				"Open MyTorrents", "IconBar.enabled", "Wizard Completed",
				"welcome.version.lastshown",
		};

		for (int i=0; i<passthrough_params.length; i++) {
			external_to_internal_key_map.put(passthrough_params[i], passthrough_params[i]);
		}
	}

	private HashMap<PluginConfigListener, COConfigurationListener> listenersPluginConfig;

	public void checkValidCoreParam(String name) {
		if (!external_to_internal_key_map.containsKey(name)) {
			throw new IllegalArgumentException("invalid core parameter: " + name);
		}
	}

	private static Map	fake_values_when_disabled;
	private static int	fake_values_ref_count;

	public static void
	setEnablePluginCoreConfigChange(
		boolean		enabled )
	{
		synchronized( PluginConfigImpl.class ){

			if ( enabled ){

				fake_values_ref_count--;

				if ( fake_values_ref_count == 0 ){

						// TODO: we could try and recover the faked values at this point

					fake_values_when_disabled = null;
				}

			}else{
				fake_values_ref_count++;

				if ( fake_values_ref_count == 1 ){

					fake_values_when_disabled = new HashMap();
				}
			}
		}
	}

	private static Object
	getFakeValueWhenDisabled(
		String	key,
		String	name )
	{
		if ( name.startsWith(key)){
			return( null );
		}

		synchronized( PluginConfigImpl.class ){

			if ( fake_values_when_disabled != null ){

				return( fake_values_when_disabled.get( name ));
			}
		}

		return( null );
	}

	private static boolean
	setFakeValueWhenDisabled(
		String	key,
		String	name,
		Object	value )
	{
		if ( name.startsWith(key)){
			return( false );
		}

		synchronized( PluginConfigImpl.class ){

			if ( fake_values_when_disabled != null ){

				fake_values_when_disabled.put( name, value );

				return( true );
			}
		}

		return( false );
	}

	private PluginInterface	plugin_interface;
	private String keyPrefix;
	private boolean         allow_key_modification;

	public
	PluginConfigImpl(
		PluginInterface		_plugin_interface,
		String			 	_key )
	{
		plugin_interface	= _plugin_interface;

		keyPrefix = _key + ".";
		allow_key_modification = true;
	}

	@Override
	public boolean
	isNewInstall()
	{
		return( COConfigurationManager.isNewInstall());
	}

	@Override
	public String
	getPluginConfigKeyPrefix()
	{
		return keyPrefix;
	}

	@Override
	public void setPluginConfigKeyPrefix(String _key) {
		if (!allow_key_modification) {
			throw new RuntimeException("cannot modify key prefix - already in use");
		}

		if (_key.length() > 0 || plugin_interface.getPluginState().isBuiltIn()) {
			keyPrefix = _key;
		} else {
			throw (new RuntimeException("Can't set Plugin Config Key Prefix to '"
					+ _key + "'"));
		}
	}

	//
	//
	// Helper methods which do everything required to get a parameter value.
	//
	//
	private boolean getBooleanParameter(String name, boolean _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return(((Boolean)obj).booleanValue());
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setBooleanDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getBooleanParameter(name);
	}

	private int[] getColorParameter(String name, int[] _default, boolean map_name, boolean set_default) {
		Object obj = getFakeValueWhenDisabled(keyPrefix, name);
		if (obj != null) {return (int[])obj;}

		if (map_name) {name = mapKeyName(name, false);}
		int[] result = getColorParameter0(name, _default, set_default);

		// Result array...
		if (result == null) {return null;}
		if (result.length == 3) {
			int[] result2 = new int[4];
			System.arraycopy(result, 0, result2, 0, 3);

			// Not sure what's the default result to return here for the override flag.
			//
			// I've just chosen zero for now.
			result2[3] = getIntParameter(name, 0, false, false);
			result = result2;
		}

		return result;
	}

	private int[] getColorParameter0(String name, int[] _default, boolean set_default) {
		Object obj = getFakeValueWhenDisabled(keyPrefix, name);
		if (obj != null){
			return((int[])obj);
		}

		notifyRGBParamExists(name);
		if (set_default) {

			// No idea what to do with the override flag, and no idea what to do
			// when the value is null.
			if (_default != null) {
				COConfigurationManager.setIntDefault(name + ".red", _default[0]);
				COConfigurationManager.setIntDefault(name + ".green", _default[1]);
				COConfigurationManager.setIntDefault(name + ".blue", _default[2]);
			}

			else {
				// I'm not expecting this branch to be executed by any callers.
				throw new RuntimeException("color parameter default is null");
			}

		}
		else if (!hasParameter(name + ".red")) {return _default;}

		return new int[] {
			COConfigurationManager.getIntParameter(name + ".red"),
			COConfigurationManager.getIntParameter(name + ".green"),
			COConfigurationManager.getIntParameter(name + ".blue"),
			COConfigurationManager.getIntParameter(name + ".override"),
		};
	}

	private byte[] getByteParameter(String name, byte[] _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return((byte[])obj);
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setByteDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getByteParameter(name);
	}

	private float getFloatParameter(String name, float _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return(((Float)obj).floatValue());
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setFloatDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getFloatParameter(name);
	}

	private int getIntParameter(String name, int _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return(((Long)obj).intValue());
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setIntDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getIntParameter(name);
	}

	private long getLongParameter(String name, long _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return(((Long)obj).longValue());
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setLongDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getLongParameter(name);
	}

	private String getStringParameter(String name, String _default, boolean map_name, boolean set_default) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return((String)obj);
		}
		if (map_name) {name = mapKeyName(name, false);}

		notifyParamExists(name);
		if (set_default) {COConfigurationManager.setStringDefault(name, _default);}
		else if (!hasParameter(name)) {return _default;}
		return COConfigurationManager.getStringParameter(name);
	}

	//
	//
	// Variants of the methods above, but which use the values from ConfigurationDefaults.
	//
	//
	private boolean getDefaultedBooleanParameter(String name, boolean map_name) {
		Object	obj = getFakeValueWhenDisabled(keyPrefix, name);
		if ( obj != null ){
			return(((Boolean)obj).booleanValue());
		}
		return getBooleanParameter(name, ConfigurationDefaults.def_boolean == 1, map_name, false);
	}

	private byte[] getDefaultedByteParameter(String name, boolean map_name) {
		return getByteParameter(name, ConfigurationDefaults.def_bytes, map_name, false);
	}

	private int[] getDefaultedColorParameter(String name, boolean map_name) {
		int[] default_value = new int[] {
				ConfigurationDefaults.def_int,
				ConfigurationDefaults.def_int,
				ConfigurationDefaults.def_int,
				1
		};
		return getColorParameter(name, default_value, map_name, false);
	}

	private float getDefaultedFloatParameter(String name, boolean map_name) {
		return getFloatParameter(name, ConfigurationDefaults.def_float, map_name, false);
	}

	private int getDefaultedIntParameter(String name, boolean map_name) {
		return getIntParameter(name, ConfigurationDefaults.def_int, map_name, false);
	}

	private long getDefaultedLongParameter(String name, boolean map_name) {
		return getLongParameter(name, ConfigurationDefaults.def_long, map_name, false);
	}

	private String getDefaultedStringParameter(String name, boolean map_name) {
		return getStringParameter(name, ConfigurationDefaults.def_String, map_name, false);
	}



	//
	//
	// Core get parameter methods (backwardly compatible).
	//
	//

	//
	//
	// Core get parameter methods (newly named ones).
	//
	//

	@Override
	public boolean getCoreBooleanParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedBooleanParameter(name, true);
	}

	@Override
	public byte[] getCoreByteParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedByteParameter(name, true);
	}

	@Override
	public int[] getCoreColorParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedColorParameter(name, true);
	}

	@Override
	public float getCoreFloatParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedFloatParameter(name, true);
	}

	@Override
	public int getCoreIntParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedIntParameter(name, true);
	}

	@Override
	public long getCoreLongParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedLongParameter(name, true);
	}

	@Override
	public String getCoreStringParameter(String name) {
		checkValidCoreParam(name);
		return getDefaultedStringParameter(name, true);
	}

	//
	//
	// Core set parameter methods (newly named ones).
	//
	//
    @Override
    public void setCoreBooleanParameter(String name, boolean value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, Boolean.valueOf(value))){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }

    @Override
    public void setCoreByteParameter(String name, byte[] value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, value )){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }

    @Override
    public void setCoreColorParameter(String name, int[] value) {
    	setCoreColorParameter(name, value, true);
    }

    @Override
    public void setCoreColorParameter(String name, int[] value, boolean override) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, value)) {
			return;
		}
		COConfigurationManager.setRGBParameter(mapKeyName(name, true), value, override);
    }

    @Override
    public void setCoreFloatParameter(String name, float value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Float( value))){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }

    @Override
    public void setCoreIntParameter(String name, int value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Long( value))){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }

    @Override
    public void setCoreLongParameter(String name, long value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Long( value))){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }

    @Override
    public void setCoreStringParameter(String name, String value) {
    	checkValidCoreParam(name);
		if ( setFakeValueWhenDisabled(keyPrefix, name, value)){
			return;
		}
    	COConfigurationManager.setParameter(mapKeyName(name, true), value);
    }


	//
	//
	// Plugin get parameter methods.
	//
	//
	@Override
	public boolean getPluginBooleanParameter(String name) {
		return getDefaultedBooleanParameter(this.keyPrefix + name, false);
	}

	@Override
	public boolean getPluginBooleanParameter(String name, boolean default_value) {
		return getBooleanParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public byte[] getPluginByteParameter(String name) {
		return getDefaultedByteParameter(this.keyPrefix + name, false);
	}

	@Override
	public byte[] getPluginByteParameter(String name, byte[] default_value) {
		return getByteParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public int[] getPluginColorParameter(String name) {
		return getDefaultedColorParameter(this.keyPrefix + name, false);
	}

	@Override
	public int[] getPluginColorParameter(String name, int[] default_value) {
		return getColorParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public float getPluginFloatParameter(String name) {
		return getDefaultedFloatParameter(this.keyPrefix + name, false);
	}

	@Override
	public float getPluginFloatParameter(String name, float default_value) {
		return getFloatParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public int getPluginIntParameter(String name) {
		return getDefaultedIntParameter(this.keyPrefix + name, false);
	}

	@Override
	public int getPluginIntParameter(String name, int default_value) {
		return getIntParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public long getPluginLongParameter(String name) {
		return getDefaultedLongParameter(this.keyPrefix + name, false);
	}

	@Override
	public long getPluginLongParameter(String name, long default_value) {
		return getLongParameter(this.keyPrefix + name, default_value, false, true);
	}

	@Override
	public String getPluginStringParameter(String name) {
		return getDefaultedStringParameter(this.keyPrefix + name, false);
	}

    @Override
    public String getPluginStringParameter(String name, String default_value) {
    	return getStringParameter(this.keyPrefix + name, default_value, false, true);
    }

	//
	//
	// Plugin set parameter methods.
	//
	//
	@Override
	public void setPluginParameter(String name, boolean value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginParameter(String name, byte[] value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginParameter(String name, float value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginParameter(String name, int value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginParameter(String name, long value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginParameter(String name, String value) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setParameter(this.keyPrefix + name, value);
	}

	@Override
	public void setPluginColorParameter(String name, int[] value) {
		setPluginColorParameter(name, value, true);
	}

	@Override
	public void setPluginColorParameter(String name, int[] value,
			boolean override) {
		notifyParamExists(this.keyPrefix + name);
		COConfigurationManager.setRGBParameter(this.keyPrefix + name, value,
				override);
	}

   	//
	//
	// Core "unsafe" get parameter methods.
	//
	//

	@Override
	public boolean getUnsafeBooleanParameter(String name) {
		return getDefaultedBooleanParameter(name, false);
	}

	@Override
	public boolean getUnsafeBooleanParameter(String name, boolean default_value) {
		return getBooleanParameter(name, default_value, false, false);
	}

	@Override
	public byte[] getUnsafeByteParameter(String name) {
		return getDefaultedByteParameter(name, false);
	}

	@Override
	public byte[] getUnsafeByteParameter(String name, byte[] default_value) {
		return getByteParameter(name, default_value, false, false);
	}

	@Override
	public int[] getUnsafeColorParameter(String name) {
		return getDefaultedColorParameter(name, false);
	}

	@Override
	public int[] getUnsafeColorParameter(String name, int[] default_value) {
		return getColorParameter(name, default_value, false, false);
	}

	@Override
	public float getUnsafeFloatParameter(String name) {
		return getDefaultedFloatParameter(name, false);
	}

	@Override
	public float getUnsafeFloatParameter(String name, float default_value) {
		return getFloatParameter(name, default_value, false, false);
	}

	@Override
	public int getUnsafeIntParameter(String name) {
		return getDefaultedIntParameter(name, false);
	}

	@Override
	public int getUnsafeIntParameter(String name, int default_value) {
		return getIntParameter(name, default_value, false, false);
	}

	@Override
	public long getUnsafeLongParameter(String name) {
		return getDefaultedLongParameter(name, false);
	}

	@Override
	public long getUnsafeLongParameter(String name, long default_value) {
		return getLongParameter(name, default_value, false, false);
	}

	@Override
	public String getUnsafeStringParameter(String name) {
		return getDefaultedStringParameter(name, false);
	}

    @Override
    public String getUnsafeStringParameter(String name, String default_value) {
    	return getStringParameter(name, default_value, false, false);
    }

	//
	//
	// Core "unsafe" set parameter methods.
	//
	//
    @Override
    public void setUnsafeBooleanParameter(String name, boolean value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, Boolean.valueOf(value))){
			return;
		}
		notifyParamExists(name);
		COConfigurationManager.setParameter(name, value);
    }

    @Override
    public void setUnsafeByteParameter(String name, byte[] value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, value)){
			return;
		}
		notifyParamExists(name);
		COConfigurationManager.setParameter(name, value);
    }

    @Override
    public void setUnsafeColorParameter(String name, int[] value) {
    	setUnsafeColorParameter(name, value, true);
    }

    @Override
    public void setUnsafeColorParameter(String name, int[] value, boolean override) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, value)){
			return;
		}
   		notifyRGBParamExists(name);
   		COConfigurationManager.setRGBParameter(name, value, override);
    }

    @Override
    public void setUnsafeFloatParameter(String name, float value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Float( value))){
			return;
		}
		notifyParamExists(name);
    	COConfigurationManager.setParameter(name, value);
    }

    @Override
    public void setUnsafeIntParameter(String name, int value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Long( value))){
			return;
		}
		notifyParamExists(name);
    	COConfigurationManager.setParameter(name, value);
    }

    @Override
    public void setUnsafeLongParameter(String name, long value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, new Long( value))){
			return;
		}
		notifyParamExists(name);
    	COConfigurationManager.setParameter(name, value);
    }

    @Override
    public void setUnsafeStringParameter(String name, String value) {
		if ( setFakeValueWhenDisabled(keyPrefix, name, value )){
			return;
		}
		notifyParamExists(name);
    	COConfigurationManager.setParameter(name, value);
    }

    //
    //
    // Get/set plugin list/map methods.
    //
    //

	@Override
	public String[] getPluginStringListParameter(String key) {
		notifyParamExists(this.keyPrefix + key);
		List<String> val = COConfigurationManager.getStringListParameter(
				this.keyPrefix + key);
		return val.toArray(new String[val.size()]);
	}

	@Override
	public void setPluginStringListParameter(String key, String[] value) {
		notifyParamExists(this.keyPrefix + key);
		COConfigurationManager.setParameter(this.keyPrefix + key,
				Arrays.asList(value));
	}

	@Override
	public List getPluginListParameter(String key, List default_value) {
		notifyParamExists(this.keyPrefix + key);
		return COConfigurationManager.getListParameter(this.keyPrefix + key,
				default_value);
	}

	@Override
	public void setPluginListParameter(String key, List value) {
		notifyParamExists(this.keyPrefix + key);
		COConfigurationManager.setParameter(this.keyPrefix + key, value);
	}

	@Override
	public Map getPluginMapParameter(String key, Map default_value) {
		notifyParamExists(this.keyPrefix + key);
		return COConfigurationManager.getMapParameter(this.keyPrefix + key,
				default_value);
	}

	@Override
	public void setPluginMapParameter(String key, Map value) {
		notifyParamExists(this.keyPrefix + key);
		COConfigurationManager.setParameter(this.keyPrefix + key, value);
	}

	@Override
	public void setPluginParameter(String key, int value, boolean global) {
		notifyParamExists(this.keyPrefix + key);
		COConfigurationManager.setParameter(this.keyPrefix + key, value);
		if (global) {
			MagnetURIHandler.getSingleton().addInfo(this.keyPrefix + key, value);
		}
	}

	@Override
	public ConfigParameter
	getParameter(
		String		key )
	{
		return( new ConfigParameterImpl( mapKeyName(key, false)));
	}

	@Override
	public ConfigParameter
	getPluginParameter(
	  	String		key )
	{
		return( new ConfigParameterImpl( this.keyPrefix +key ));
	}

	@Override
	public boolean removePluginParameter(String key) {
		notifyParamExists(this.keyPrefix + key);
		return COConfigurationManager.removeParameter(this.keyPrefix + key);
	}

	@Override
	public boolean removePluginColorParameter(String key) {
		notifyParamExists(this.keyPrefix + key);
		return COConfigurationManager.removeRGBParameter(this.keyPrefix + key);
	}

	  @Override
	  public Map
	  getUnsafeParameterList()
	  {
		  Set params = COConfigurationManager.getAllowedParameters();

		  Iterator	it = params.iterator();

		  Map	result = new HashMap();

		  while( it.hasNext()){

			  try{
				  String	name = (String)it.next();

				  Object val = COConfigurationManager.getParameter( name );

				  if ( val instanceof String || val instanceof Long ){

				  }else if ( val instanceof byte[]){

					  val = new String((byte[])val, "UTF-8" );

				  }else if ( val instanceof Integer ){

					  val = new Long(((Integer)val).intValue());

				  }else if ( val instanceof List ){

					  val = null;

				  }else if ( val instanceof Map ){

					  val = null;

				  }else if ( val instanceof Boolean ){

					  val = new Long(((Boolean)val).booleanValue()?1:0);

				  }else if ( val instanceof Float || val instanceof Double ){

					  val = val.toString();
				  }

				  if ( val != null ){

					  result.put( name, val );
				  }
			  }catch( Throwable e ){

				  Debug.printStackTrace(e);
			  }
		  }

		  return( result );
	  }

	@Override
	public void
	save()
	{
		/**
		 * We won't redirect the save method to the external source if there is one
		 * (despite that being the previous behaviour) - the plugin might be setting
		 * core values.
		 */
		//if (this.external_source != null) {this.external_source.save(true);}
		COConfigurationManager.save();
	}

	@Override
	public File
	getPluginUserFile(
		String	name )
	{

		String	dir = plugin_interface.getUtilities().getUserDir();

		File	file = FileUtil.newFile( dir, "plugins" );

		String	p_dir = plugin_interface.getPluginDirectoryName();

		if ( p_dir.length() != 0 ){

			int	lp = p_dir.lastIndexOf(File.separatorChar);

			if ( lp != -1 ){

				p_dir = p_dir.substring(lp+1);
			}

			file = FileUtil.newFile( file, p_dir );

		}else{

			String	id = plugin_interface.getPluginID();

			if ( id.length() > 0 && !id.equals( PluginInitializer.INTERNAL_PLUGIN_ID )){

				file = FileUtil.newFile( file, id );

			}else{

				throw( new RuntimeException( "Plugin was not loaded from a directory" ));
			}
		}


		FileUtil.mkdirs(file);

		return( FileUtil.newFile( file, name ));
	}

	@Override
	public void
	addListener(
		final PluginConfigListener	l )
	{
		if (listenersPluginConfig != null && listenersPluginConfig.containsKey(l)) {
			return;
		}
		COConfigurationListener listener = new COConfigurationListener() {
			@Override
			public void
			configurationSaved() {
				l.configSaved();
			}
		};
		if (listenersPluginConfig == null) {
			listenersPluginConfig = new HashMap<>();
		}
		listenersPluginConfig.put(l, listener);
		COConfigurationManager.addListener(listener);
	}

	@Override
	public void removeListener(PluginConfigListener l) {
		if (listenersPluginConfig == null) {
			return;
		}
		COConfigurationListener configListener = listenersPluginConfig.remove(l);
		if (configListener != null) {
			COConfigurationManager.removeListener(configListener);
		}
	}

	private String mapKeyName(String key, boolean for_set) {
		String result = (String)external_to_internal_key_map.get(key);
		if (result == null) {
			if (for_set) {
				throw new RuntimeException("No permission to set the value of core parameter: " + key);
			}
			else {
				return key;
			}
		}
		return result;
	}

	@Override
	public boolean hasParameter(String param_name) {
		// Don't see any reason why a plugin should care whether it is looking
		// at a system default setting or not, so we'll do an implicit check.
		return COConfigurationManager.hasParameter(param_name, false);
	}

	@Override
	public boolean hasPluginParameter(String param_name) {
		// We should not have default settings for plugins in configuration
		// defaults, so we don't bother doing an implicit check.
		notifyParamExists(this.keyPrefix + param_name);
		return COConfigurationManager.hasParameter(this.keyPrefix + param_name, true);
	}

	public void notifyRGBParamExists(String param) {
		notifyParamExists(param + ".red");
		notifyParamExists(param + ".blue");
		notifyParamExists(param + ".green");
		notifyParamExists(param + ".override");
	}

	// Not exposed in the plugin API.
	public void notifyParamExists(String param) {
		if (allow_key_modification && param.startsWith(this.keyPrefix)) {
			allow_key_modification = false;
		}
		if (external_source != null && param.startsWith(this.keyPrefix)) {
			external_source.registerParameter(param);
		}
	}

	@Override
	public PluginConfigSource enableExternalConfigSource() {
		PluginConfigSourceImpl source = new PluginConfigSourceImpl(this, this.plugin_interface.getPluginID());
		setPluginConfigSource(source);
		return source;
	}

	@Override
	public PluginConfigSource getPluginConfigSource() {
		return this.external_source;
	}

	@Override
	public void setPluginConfigSource(PluginConfigSource source) {
		if (this.external_source != null) {
			throw new RuntimeException("external config source already associated!");
		}

		// We need a common key prefix, otherwise this won't work correctly.
		PluginConfigSourceImpl source_impl = (PluginConfigSourceImpl)source;
	    String used_key = source_impl.getUsedKeyPrefix();
	    if (used_key != null && !this.getPluginConfigKeyPrefix().startsWith(used_key)) {
	    	throw new RuntimeException("cannot use this config source object - incompatible prefix keys: " + used_key + " / " + this.getPluginConfigKeyPrefix());
	    }
		this.external_source = (PluginConfigSourceImpl)source;
	}

}
