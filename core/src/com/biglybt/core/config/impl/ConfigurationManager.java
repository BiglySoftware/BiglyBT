/*
 * Created on Jun 20, 2003
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
package com.biglybt.core.config.impl;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.COConfigurationManager.ParameterVerifier;
import com.biglybt.core.config.COConfigurationManager.ResetToDefaultsListener;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.PriorityParameterListener;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.util.*;

/**
 * A singleton used to store configuration into a bencoded file.
 *
 * @author TdC_VgA
 *
 */
public class
ConfigurationManager
	implements AEDiagnosticsEvidenceGenerator
{
  private static final boolean DEBUG_PARAMETER_LISTENERS = false;
  // Installer will migrate different named config
	public static final String CONFIG_FILENAME = "biglybt.config";

	private static ConfigurationManager 	config_temp = null;
  private static ConfigurationManager 	config 		= null;
  private static final AEMonitor				class_mon	= new AEMonitor( "ConfigMan:class" );


  private ConcurrentHashMapWrapper<String,Object> propertiesMap;	// leave this NULL - it picks up errors caused by initialisation sequence errors
  private final List transient_properties     = new ArrayList();

  private final List<COConfigurationListener>		listenerz 			= new ArrayList<>();
	private final Map<String,ParameterListener[]> 	parameterListenerz 	= new HashMap<>();
	private final Map<String,List<WeakReference<ParameterListener>>> 	weakParameterListenerz 	= new HashMap<>();

  private final List<ResetToDefaultsListener>	reset_to_def_listeners = new ArrayList<>();

  private static final FrequencyLimitedDispatcher dirty_dispatcher =
	  new FrequencyLimitedDispatcher(
			  new AERunnable()
			  {
				  @Override
				  public void
				  runSupport()
				  {
					  COConfigurationManager.save();
				  }
			  },
			  30*1000 );

  private final ParameterListener
	exportable_parameter_listener =
		new ParameterListener() {

			@Override
			public void
			parameterChanged(
				String key )
			{
				updateExportableParameter( key );
			}
		};

  private final Map<String,String[]>	exported_parameters = new HashMap<>();
  private final Map<String,String>	imported_parameters	= new HashMap<>();
  private volatile boolean		exported_parameters_dirty;


  public static ConfigurationManager getInstance() {
  	try{
  		class_mon.enter();

	  	if ( config == null){

	  			// this is nasty but I can't see an easy way around it. Unfortunately while reading the config
	  			// we hit other code (logging for example) that needs access to the config data. Things are
	  			// cunningly (?) arranged so that a recursive call here *won't* result in a further (looping)
	  			// recursive call if we attempt to load the config again. Hence this disgusting code that
	  			// goes for a second load attempt

	  		if ( config_temp == null ){

	  			config_temp = new ConfigurationManager();

	  			config_temp.load();

	  			config_temp.initialise();

	  		  	config	= config_temp;

	  		}else{

	  			if ( config_temp.propertiesMap == null ){

	  				config_temp.load();
	  			}

	  			return( config_temp );
	  		}
	  	}

	  	return config;

  	}finally{
  		class_mon.exit();
  	}
  }

  public static ConfigurationManager getInstance(Map data) {
  	try{
  		class_mon.enter();

	  	if (config == null){

	  		config = new ConfigurationManager(data);
	  	}

	  	return config;
  	}finally{

  		class_mon.exit();
  	}
  }


  private
  ConfigurationManager()
  {
  }

  private
  ConfigurationManager(
  	Map data )
  {
	  	// default state of play for config initialised from map is debug log files off unless already
	  	// specified

	  if ( data.get("Logger.DebugFiles.Enabled") == null ){

		  data.put( "Logger.DebugFiles.Enabled", new Long(0));
	  }

	  propertiesMap	= new ConcurrentHashMapWrapper<String,Object>( data );
  }

  protected void
  initialise()
  {

	  //ConfigurationChecker.migrateConfig();  //removed 2201

	 ConfigurationChecker.checkConfiguration();

	 ConfigurationChecker.setSystemProperties();

	 loadExportedParameters();

	 AEDiagnostics.addWeakEvidenceGenerator( this );
  }

  public void load(String filename)
  {
  	Map	data = FileUtil.readResilientConfigFile( filename, false );

  		// horrendous recursive loading going on here due to logger + config depedencies. If already loaded
  		// then use the existing data as it might have already been written to...

  	if ( propertiesMap == null ){

  		ConcurrentHashMapWrapper<String,Object> c_map = new ConcurrentHashMapWrapper<>(data.size() + 256, 0.75f, 8);

  		c_map.putAll( data );

  		propertiesMap	= c_map;
  	}

/*
 * Can't do this yet.  Sometimes, there's a default set to x, but the code
 * calls get..Parameter(..., y).  y != x.  When the user sets the the parameter
 * to x, we remove it from the list.  Later, the get..Parameter(.., y) returns
 * y because there is no entry.
 *
 * The solution is to not allow get..Parameter(.., y) when there's a default
 * value.  Another reason to not allow it is that having two defaults confuses
 * coders.
 *
  	// Remove entries that are default.  Saves memory, reduces
  	// file size when saved again
    ConfigurationDefaults def = ConfigurationDefaults.getInstance();
  	Iterator it = new TreeSet(propertiesMap.keySet()).iterator();

		while (it.hasNext()) {
			String key = (String)it.next();
			Object defValue = def.getDefaultValueAsObject(key);
			if (defValue == null)
				continue;

			if (defValue instanceof Long) {
				int iDefValue = ((Long)defValue).intValue();
				int iValue = getIntParameter(key, iDefValue);
				if (iValue == iDefValue)
					propertiesMap.remove(key);
			}
			if (defValue instanceof String) {
				String sDefValue = defValue.toString();
				String sValue = getStringParameter(key, sDefValue);
				if (sValue.compareTo(sDefValue) == 0)
					propertiesMap.remove(key);
			}
		}
*/
  }

  public void load() {
    load(CONFIG_FILENAME);

    try {
      String[] keys = propertiesMap.keySet().toArray(new String[0]);
      for (String key : keys) {
      	if (key == null) {
      		continue;
      	}
  			if (key.startsWith("SideBar.Expanded.AutoOpen.") || key.startsWith("NameColumn.wrapText.")) {
  				removeParameter(key);
  			}
  		}
    } catch (Exception e) {
    	// not sure if I can do Debug.out here.. could be in that evil
    	// preinitialization loop of dooom
    	e.printStackTrace();
    }
  }

  public void save(String filename)
  {
	if ( propertiesMap == null ){

			// nothing to save, initialisation not complete

		return;
	}

	/**
	 * Note - propertiesMap isn't synchronised! We'll clone the map
	 * now, because we need to modify it. The BEncoding code will
	 * create a new map object (TreeMap) because it needs to be
	 * sorted, so we might as well do it here too.
	 */
	TreeMap<String,Object> properties_clone = propertiesMap.toTreeMap();

	// Remove any transient parameters.
	if (!this.transient_properties.isEmpty()) {
		properties_clone.keySet().removeAll(this.transient_properties);
	}

  	FileUtil.writeResilientConfigFile( filename, properties_clone );

  	List<COConfigurationListener>	listeners_copy;

  	synchronized( listenerz ){

    	listeners_copy = new ArrayList<>(listenerz);
    }

	for (int i=0;i<listeners_copy.size();i++){

		COConfigurationListener l = (COConfigurationListener)listeners_copy.get(i);

		if (l != null){

			try{
				l.configurationSaved();

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}else{

			Debug.out("COConfigurationListener is null");
		}
	}

	if ( exported_parameters_dirty ){

		exportParameters();
	}
  }

  public void save() {
    save(CONFIG_FILENAME);
  }

	public void
	setDirty()
	{
		dirty_dispatcher.dispatch();
	}

	public boolean
	isNewInstall()
	{
		return( ConfigurationChecker.isNewInstall());
	}

	public Set<String>
	getDefinedParameters()
	{
		return(new HashSet<>(propertiesMap.keySet()));
	}

  public boolean getBooleanParameter(String parameter, boolean defaultValue) {
    int defaultInt = defaultValue ? 1 : 0;
    int result = getIntParameter(parameter, defaultInt);
    return result == 0 ? false : true;
  }

  public boolean getBooleanParameter(String parameter) {
    ConfigurationDefaults def = ConfigurationDefaults.getInstance();
    int result;
    try {
      result = getIntParameter(parameter, def.getIntParameter(parameter));
    } catch (ConfigurationParameterNotFoundException e) {
      result = getIntParameter(parameter, ConfigurationDefaults.def_boolean);
    }
    return result == 0 ? false : true;
  }

  public boolean setParameter(String parameter, boolean value) {
    return setParameter(parameter, value ? 1 : 0);
  }

  private Long getLongParameterRaw(String parameter) {
    try {
      return (Long) propertiesMap.get(parameter);
    } catch (Exception e) {
      Debug.out( "Parameter '" + parameter + "' has incorrect type", e );
      return null;
    }
  }

  public int getIntParameter(String parameter, int defaultValue) {
    Long tempValue = getLongParameterRaw(parameter);
    return tempValue != null ? tempValue.intValue() : defaultValue;
  }

  public int getIntParameter(String parameter) {
  	ConfigurationDefaults def = ConfigurationDefaults.getInstance();
  	int result;
    try {
      result = getIntParameter(parameter, def.getIntParameter(parameter));
    } catch (ConfigurationParameterNotFoundException e) {
      result = getIntParameter(parameter, ConfigurationDefaults.def_int);
    }
    return result;
  }

  public long getLongParameter(String parameter, long defaultValue) {
    Long tempValue = getLongParameterRaw(parameter);
    return tempValue != null ? tempValue.longValue() : defaultValue;
  }

  public long getLongParameter(String parameter) {
  	ConfigurationDefaults def = ConfigurationDefaults.getInstance();
  	long result;
    try {
      result = getLongParameter(parameter, def.getLongParameter(parameter));
    } catch (ConfigurationParameterNotFoundException e) {
      result = getLongParameter(parameter, ConfigurationDefaults.def_long);
    }
    return result;
  }

  private byte[] getByteParameterRaw(String parameter) {
    return (byte[]) propertiesMap.get(parameter);
  }

  public byte[] getByteParameter(String parameter) {
	  	ConfigurationDefaults def = ConfigurationDefaults.getInstance();
	  	byte[] result;
	    try {
	      result = getByteParameter(parameter, def.getByteParameter(parameter));
	    } catch (ConfigurationParameterNotFoundException e) {
	      result = getByteParameter(parameter, ConfigurationDefaults.def_bytes);
	    }
	    return result;
	  }

  public byte[] getByteParameter(String parameter, byte[] defaultValue) {
    byte[] tempValue = getByteParameterRaw(parameter);
    return tempValue != null ? tempValue : defaultValue;
  }

  private String getStringParameter(String parameter, byte[] defaultValue) {
	  byte[] bp = getByteParameter(parameter, defaultValue);
	  if ( bp == null ){
		  bp = getByteParameter(parameter, null);
	  }
      if (bp == null)
        return null;
      return bytesToString(bp);
  }

  public String getStringParameter(String parameter, String defaultValue) {
    String tempValue = getStringParameter(parameter, (byte[]) null);
    return tempValue != null ? tempValue : defaultValue;
  }

  public String getStringParameter(String parameter) {
    ConfigurationDefaults def = ConfigurationDefaults.getInstance();
    String result;
    try {
      result = getStringParameter(parameter, def.getStringParameter(parameter));
    } catch (ConfigurationParameterNotFoundException e) {
      result = getStringParameter(parameter, ConfigurationDefaults.def_String);
    }
    return result;
  }

  public List<String> getStringListParameter(String parameter) {
  	try {
  		List rawList = (List) propertiesMap.get(parameter);
  		if(rawList != null) {
			  return BDecoder.decodeStrings(new ArrayList(rawList));
		  }
  	} catch(Exception e) {
  		Debug.out( "Parameter '" + parameter + "' has incorrect type", e );
  	}
	  return new ArrayList<>();
  }

	public List
  getListParameter(String parameter, List def)
  {
  	try {
  		List rawList = (List) propertiesMap.get(parameter);
  		if(rawList == null)
  			return def;
  		return rawList;
  	} catch(Exception e) {
  		Debug.out( "Parameter '" + parameter + "' has incorrect type", e );
  		return def;
  	}
  }

  public boolean setParameter(String parameter,List value) {
  	try {
  		propertiesMap.put(parameter,value);
  		notifyParameterListeners(parameter);
  	} catch(Exception e) {
  		Debug.printStackTrace(e);
  		return false;
  	}
  	return true;
  }

  public Map
  getMapParameter(String parameter, Map def)
  {
  	try {
		Map map = (Map) propertiesMap.get(parameter);
  		if(map == null)
  			return def;
  		return map;
  	} catch(Exception e) {
  		Debug.out( "Parameter '" + parameter + "' has incorrect type", e );
  		return def;
  	}
  }

  public boolean setParameter(String parameter,Map value) {
  	try {
  		propertiesMap.put(parameter,value);
  		notifyParameterListeners(parameter);
  	} catch(Exception e) {
  		Debug.printStackTrace(e);
  		return false;
  	}
  	return true;
  }


  public String getDirectoryParameter(String parameter) throws IOException {
    String dir = getStringParameter(parameter);

    if( dir.length() > 0 ) {
      File temp = FileUtil.newFile(dir);
      if (!temp.exists()) {
      	FileUtil.mkdirs(temp);
      }
      if (!temp.isDirectory()) {
        throw new IOException("Configuration error. This is not a directory: " + dir);
      }
    }

    return dir;
  }

  public float getFloatParameter(String parameter) {
	  return( getFloatParameter( parameter, ConfigurationDefaults.def_float ));
  }

  public float getFloatParameter(String parameter, float def_val) {
    ConfigurationDefaults def = ConfigurationDefaults.getInstance();
    try {
      Object o = propertiesMap.get(parameter);
      if (o instanceof Number) {
        return ((Number)o).floatValue();
      }

      String s = getStringParameter(parameter);

      if (!s.equals(ConfigurationDefaults.def_String))
        return Float.parseFloat(s);
    } catch (Exception e) {
    	Debug.out( "Parameter '" + parameter + "' has incorrect type", e );
    }

    try {
      return def.getFloatParameter(parameter);
    } catch (Exception e2) {
      return def_val;
    }
  }

  public boolean setParameter(String parameter, float defaultValue) {
    String newValue = String.valueOf(defaultValue);
    return setParameter(parameter, stringToBytes(newValue));
  }

  public boolean setParameter(String parameter, int defaultValue) {
		Long newValue = new Long(defaultValue);
		try {
			Long oldValue = (Long) propertiesMap.put(parameter, newValue);
			return notifyParameterListenersIfChanged(parameter, newValue, oldValue);
		} catch (ClassCastException e) {
			// Issuing a warning here would be nice, but both logging and config stuff
			// at startup create potential deadlocks or stack overflows
			notifyParameterListeners(parameter);
			return true;
		}
	}

	public boolean setParameter(String parameter, long defaultValue) {
		Long newValue = new Long(defaultValue);
		try {
			Long oldValue = (Long) propertiesMap.put(parameter, newValue);
			return notifyParameterListenersIfChanged(parameter, newValue, oldValue);
		} catch (ClassCastException e) {
			// Issuing a warning here would be nice, but both logging and config stuff
			// at startup create potential deadlocks or stack overflows
			notifyParameterListeners(parameter);
			return true;
		}
	}

	public boolean setParameter(String parameter, byte[] defaultValue) {
		try {
			byte[] oldValue = (byte[]) propertiesMap.put(parameter, defaultValue);
			return notifyParameterListenersIfChanged(parameter, defaultValue,
					oldValue);
		} catch (ClassCastException e) {
			// Issuing a warning here would be nice, but both logging and config stuff
			// at startup create potential deadlocks or stack overflows
			notifyParameterListeners(parameter);
			return true;
		}
	}

  public boolean setParameter(String parameter, String defaultValue) {
    return setParameter(parameter, stringToBytes(defaultValue));
  }

  /**
   * Returns true if a parameter with the given name exists.
   * @param key The name of the parameter to check.
   * @param explicit If <tt>true</tt>, we only check for a value which is
   *     definitely stored explicitly, <tt>false</tt> means that we'll also
   *     check against configuration defaults too.
   */
  public boolean hasParameter(String key, boolean explicit) {

	  // We have an explicit value set.
	  if (propertiesMap.containsKey(key)) {return true;}

	  // We have a default value set.
	  if ((!explicit) && ConfigurationDefaults.getInstance().hasParameter(key)) {
		  return true;
	  }

	  return false;
  }

  public boolean
  verifyParameter(
	String parameter,
	String value )
  {
	  List verifiers = ConfigurationDefaults.getInstance().getVerifiers(parameter);

	  if ( verifiers != null ){
		  try{
			  for (int i=0;i<verifiers.size();i++){

				  ParameterVerifier	verifier = (ParameterVerifier)verifiers.get(i);

				  if ( verifier != null ){

					  try{
						  if ( !verifier.verify(parameter,value)){

							  return( false );
						  }
					  }catch( Throwable e ){

						  Debug.printStackTrace( e );
					  }
				  }
			  }
		  }catch( Throwable e ){

			  // we're not synchronized so possible but unlikely error here

			  Debug.printStackTrace( e );
		  }
	  }

	  return( true );
  }

	public boolean setRGBParameter(String parameter, int red, int green, int blue, Boolean override) {
    boolean bAnyChanged = false;
    bAnyChanged |= setParameter(parameter + ".red", red);
    bAnyChanged |= setParameter(parameter + ".green", green);
    bAnyChanged |= setParameter(parameter + ".blue", blue);
    if (override != null) {
	    bAnyChanged |= setParameter(parameter + ".override", override);
    }
    if (bAnyChanged)
      notifyParameterListeners(parameter);

    return bAnyChanged;
	}

	public boolean setRGBParameter(String parameter, int[] rgb, Boolean override) {
  	if (rgb != null) {
  		if (rgb.length < 3) {
				System.err.println("Invalid array for setRGBParameter(\"" + parameter
						+ "\", " + Arrays.toString(rgb) + ", " + override);
				return false;
		  }
		  return setRGBParameter(parameter, rgb[0], rgb[1], rgb[2], override);
	  }

		boolean changed = false;
		changed |= removeParameter(parameter + ".override");
		changed |= removeParameter(parameter + ".red");
		changed |= removeParameter(parameter + ".green");
		changed |= removeParameter(parameter + ".blue");

		if (changed) {
			notifyParameterListeners(parameter);
		}
		return changed;
	}

	public int[] getRGBParameter(String parameter) {
		
		int	r = getIntParameter( parameter + ".red", -1 );
		int	g = getIntParameter( parameter + ".green", -1 );
		int	b = getIntParameter( parameter + ".blue", -1 );
		
		if ( 	r < 0 || r > 255 ||
				g < 0 || g > 255 ||
				b < 0 || b > 255 ){
			
			return( null );
		}
		
		return( new int[]{ r, g, b });
	}	
	
	public Object
  getParameter(
	String	name )
  {
	  Object value = propertiesMap.get( name );

	  if ( value == null ){

		  value = ConfigurationDefaults.getInstance().getParameter( name );
	  }

	  return( value );
  }

  /**
   * Set the raw parameter value to store in the properties map. This should
   * only be used by trusted callers, and has been added to support external
   * plugin config files.
   *
   * @param parameter Parameter name.
   * @param value A bencode-ably safe value.
   */
  public void setParameterRawNoNotify(String parameter, Object value) {
	  this.propertiesMap.put(parameter, value);
  }

  /**
   * Use this method to record a parameter as one which can be stored
   * here, but shouldn't be saved in the .config file. Instead, some external
   * object should be responsible for the parameter's persistency (if it
   * should have any at all).
   */
  public void registerTransientParameter(String param) {
	  this.transient_properties.add(param);
  }

  /**
   * Remove the given configuration parameter completely.
   * @param parameter to remove
   * @return true if found and removed, false if not
   */
  public boolean removeParameter( String parameter ) {
    boolean removed = propertiesMap.remove( parameter ) != null;
    if (removed)
    	notifyParameterListeners(parameter);
    return removed;
  }

  public boolean removeRGBParameter(String parameter) {
    boolean bAnyChanged = false;
    bAnyChanged |= removeParameter(parameter + ".red");
    bAnyChanged |= removeParameter(parameter + ".green");
    bAnyChanged |= removeParameter(parameter + ".blue");
    bAnyChanged |= removeParameter(parameter + ".override");
    if (bAnyChanged)
      notifyParameterListeners(parameter);

    return bAnyChanged;
  }

  /**
   * Does the given parameter exist.
   * @param parameter to check
   * @return true if exists, false if not present
   */

  public boolean
  doesParameterNonDefaultExist(
  	String parameter )
  {
    return propertiesMap.containsKey( parameter );
  }



  private boolean  notifyParameterListenersIfChanged(String parameter, Long newValue, Long oldValue) {
    if(oldValue == null || 0 != newValue.compareTo(oldValue)) {
      notifyParameterListeners(parameter);
      return true;
    }
    return false;
  }

  private boolean notifyParameterListenersIfChanged(String parameter, byte[] newValue, byte[] oldValue) {
    if(oldValue == null || !Arrays.equals(newValue, oldValue)) {
      notifyParameterListeners(parameter);
      return true;
    }
    return false;
  }

  public void
  addResetToDefaultsListener(
	  ResetToDefaultsListener		l )
  {
	  synchronized( reset_to_def_listeners ){

		  reset_to_def_listeners.add( l );
	  }
  }

  public void
  registerExportedParameter(
	 String		name,
	 String		key )
  {
	  synchronized( exported_parameters ){

		  String[] entry = exported_parameters.get( key );

		  if ( entry == null ){

			  entry = new String[]{ name, imported_parameters.remove( name ) };

			  exported_parameters.put( key, entry );
		  }
	  }

	  addParameterListener(
		key,
		exportable_parameter_listener );

	  updateExportableParameter( key );
  }


  void
  updateExportableParameter(
	String		key )
  {
	  Object	o_value = getParameter( key );

	  String	value;

	  if ( o_value == null ){

		  value = null;

	  }else if ( o_value instanceof byte[] ){

		  try{
			  value = new String((byte[])o_value, "UTF-8" );

		  }catch( UnsupportedEncodingException e ){

			  value = null;
		  }
	  }else{

		  value = String.valueOf( o_value );
	  }

	  synchronized( exported_parameters ){

		  String[]	entry = exported_parameters.get( key );

		  if ( entry != null ){

			  String existing = entry[1];

			  if ( existing != value ){

				  if ( existing == null || value == null || !existing.equals( value )){

					  entry[1] = value;

					  if ( !exported_parameters_dirty ){

						  exported_parameters_dirty = true;

						  new DelayedEvent(
								 "epd",
								 5000,
								 new AERunnable()
								 {

									@Override
									public void
									runSupport()
									{
										exportParameters();
									}
								});
					  }
				  }
			  }
		  }
	  }
  }

  void
  exportParameters()
  {
	  synchronized( exported_parameters ){

		  if ( !exported_parameters_dirty ){

			  return;
		  }

		  exported_parameters_dirty = false;

		  try{
			  TreeMap<String,String> tm  = new TreeMap<>();

			  Set<String>	exported_keys = new HashSet<>();

			  for ( String[] entry: exported_parameters.values()){

				  String	key		= entry[0];
				  String	value 	= entry[1];

				  exported_keys.add( key );

				  if ( value != null ){

					  tm.put( key, value );
				  }
			  }

			  for ( Map.Entry<String,String> entry: imported_parameters.entrySet()){

				  String key = entry.getKey();

				  if ( !exported_keys.contains( key)){

					  tm.put( key, entry.getValue());
				  }
			  }

			  File parent_dir = FileUtil.newFile(SystemProperties.getUserPath());

			  File props = FileUtil.newFile( parent_dir, "exported_params.properties" );

			  PrintWriter pw = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( props ), "UTF-8" ));

			  try{
				  for ( Map.Entry<String, String> entry: tm.entrySet()){

					  pw.println( entry.getKey() + "=" + entry.getValue());
				  }

			  }finally{

				  pw.close();
			  }
		  }catch( Throwable e ){

			  e.printStackTrace();
		  }
	  }
  }

  private void
  loadExportedParameters()
  {
	  synchronized( exported_parameters ){

		  try{
			  File parent_dir = FileUtil.newFile(SystemProperties.getUserPath());

			  File props = FileUtil.newFile( parent_dir, "exported_params.properties" );

			  if ( props.exists()){

				  LineNumberReader lnr = new LineNumberReader( new InputStreamReader( FileUtil.newFileInputStream( props ), "UTF-8" ));

				  try{
					  while( true ){

						  String	line = lnr.readLine();

						  if ( line == null ){

							  break;
						  }

						  String[] bits = line.split( "=" );

						  if ( bits.length == 2 ){

							  String	key 	= bits[0].trim();
							  String	value	= bits[1].trim();

							  if ( key.length() > 0 && value.length() > 0 ){

								  imported_parameters.put( key, value );
							  }
						  }
					  }
				  }finally{

					  lnr.close();
				  }
			  }
		  }catch( Throwable e ){

			  e.printStackTrace();
		  }
	  }

	  COConfigurationManager.setIntDefault( "instance.port", Constants.INSTANCE_PORT );

	  registerExportedParameter( "instance.port", "instance.port" );
  }

  public void
  resetToDefaults()
  {
	  ConfigurationDefaults def = ConfigurationDefaults.getInstance();

	  List<String> def_names = new ArrayList<>((Set<String>) def.getAllowedParameters());

	  for ( String s: def_names ){

		  if ( propertiesMap.remove( s ) != null ){

			  notifyParameterListeners( s );
		  }
	  }

	  List<ResetToDefaultsListener>	listeners;

	  synchronized( reset_to_def_listeners ){

		  listeners = new ArrayList<>(reset_to_def_listeners);
	  }

	  for ( ResetToDefaultsListener l: listeners ){

		  try{
			  l.reset();

		  }catch( Throwable e ){

			  Debug.out( e );
		  }
	  }

	  save();
  }

  private void
  notifyParameterListeners(
		String parameter)
  {
	  ParameterListener[] listeners;

	  synchronized( parameterListenerz ){

		  listeners = parameterListenerz.get(parameter);
	  }

	  List<ParameterListener> listListeners = null;
	  synchronized( weakParameterListenerz ){

		  List<WeakReference<ParameterListener>> temp = weakParameterListenerz.get(parameter);
		  if (temp != null) {
			  for (Iterator<WeakReference<ParameterListener>> iterator = temp.iterator(); iterator.hasNext(); ) {
				  ParameterListener listener = iterator.next().get();
				  if (listener == null) {
				  	iterator.remove();
				  } else {
				  	if (listListeners == null) {
				  		listListeners = new ArrayList<>(temp.size() + (listeners == null ? 0  : listeners.length));
					  }

					  if (listeners != null && !(listener instanceof PriorityParameterListener)) {
						  // listeners might have PriorityParameterListener, so append
						  // them before the first non-Priority one
						  Collections.addAll(listListeners, listeners);
						  listeners = null;
					  }

					  listListeners.add(listener);
				  }
			  }
		  }
	  }

	  if (listListeners != null) {
		  for (ParameterListener listener : listListeners) {
			  if ( listener != null ){

				  try{
					  listener.parameterChanged( parameter );

				  }catch (Throwable e) {

					  Debug.printStackTrace(e);
				  }
			  }
		  }
	  }

		if ( listeners != null) {

			for ( ParameterListener listener: listeners ) {

				if ( listener != null ){

					try{
						listener.parameterChanged( parameter );

					}catch (Throwable e) {

						Debug.printStackTrace(e);
					}
				}
			}

		}
	}


	public void
	addWeakParameterListener(
			String 				parameter,
			ParameterListener 	new_listener )
	{
		if ( parameter == null || new_listener == null ){

			return;
		}

		synchronized( weakParameterListenerz ){

			List<WeakReference<ParameterListener>> listeners = weakParameterListenerz.get( parameter );

			if ( listeners == null ){


				listeners = new ArrayList<>(1);
				listeners.add(new WeakReference<ParameterListener>(new_listener));

				weakParameterListenerz.put(parameter, listeners );

			}else{

				if ( Constants.IS_CVS_VERSION && listeners.size() > 100 ){
					Debug.out( parameter );
				}

				for (Iterator<WeakReference<ParameterListener>> iterator = listeners.iterator(); iterator.hasNext(); ) {
					ParameterListener listener = iterator.next().get();
					if (listener == null) {
						iterator.remove();
					}
					if (listener == new_listener) {
						return;
					}
				}

				WeakReference<ParameterListener> weakRef = new WeakReference<>(new_listener);
				if ( new_listener instanceof PriorityParameterListener ){
					listeners.add(0, weakRef);

				}else{
					listeners.add( weakRef);
				}
			}
		}
	}

	/**
	 * Explicitly removing a weak ParameterListener prevents it from being fired
	 * after being de-referenced, but before GC'd
	 */
	public void removeWeakParameterListener(String parameter, ParameterListener listener) {
  	synchronized (weakParameterListenerz) {
		  List<WeakReference<ParameterListener>> list = weakParameterListenerz.get(parameter);
		  if ( list != null ){
			  for (Iterator<WeakReference<ParameterListener>> iterator = list.iterator(); iterator.hasNext(); ) {
				  ParameterListener existing = iterator.next().get();
				  if (existing == null) {
				  	iterator.remove();
				  } else if (existing == listener) {
				  	iterator.remove();
				  	break;
				  }
			  }
			  if (list.size() == 0) {
			  	weakParameterListenerz.remove(parameter);
			  }
		  }
	  }
	}

	public void
  addParameterListener(
	String 				parameter,
	ParameterListener 	new_listener )
  {
    if ( parameter == null || new_listener == null ){

	      return;
    }

	  synchronized( parameterListenerz ){

	    ParameterListener[] listeners = parameterListenerz.get( parameter );

	    if ( listeners == null ){

	    	parameterListenerz.put(parameter, new ParameterListener[]{ new_listener } );

	    }else{

	    	ParameterListener[]	new_listeners = new ParameterListener[ listeners.length + 1 ];

	    	if ( Constants.IS_CVS_VERSION && listeners.length > 100 ){
	    		Debug.out( parameter );
	    	}

	    	int	pos;

	    	if ( new_listener instanceof PriorityParameterListener ){

	    		new_listeners[0] = new_listener;

	    		pos = 1;

	    	}else{

	    		new_listeners[ listeners.length ] = new_listener;

	    		pos = 0;
	    	}

	    	for ( int i=0;i<listeners.length;i++){

	    		ParameterListener existing_listener = listeners[i];

	    		if ( existing_listener == new_listener ){

	    			return;
	    		}

	    		new_listeners[pos++] = existing_listener;
	    	}

	    	if ( DEBUG_PARAMETER_LISTENERS ){

	    		System.out.println( parameter + "->" + new_listeners.length );
	    	}

	    	parameterListenerz.put( parameter, new_listeners );
	    }
  	}
  }

  public void removeParameterListener(String parameter, ParameterListener listener){

    if( parameter == null || listener == null ){
    	return;
    }

    synchronized( parameterListenerz ){
	    ParameterListener[] listeners = parameterListenerz.get( parameter );

	    if ( listeners == null ){

	    	return;
	    }

	    if ( listeners.length == 1 ){

	    	if ( listeners[0] == listener ){

	    		parameterListenerz.remove( parameter );
	    	}
	    }else{

	    	ParameterListener[] new_listeners = new ParameterListener[ listeners.length - 1 ];

	    	int	pos = 0;

	    	for ( int i=0;i<listeners.length;i++){

	    		ParameterListener existing_listener = listeners[i];

	    		if ( existing_listener != listener ){

	    			if ( pos == new_listeners.length ){

	    				return;
	    			}

	    			new_listeners[pos++] = existing_listener;
	    		}
	    	}

	    	if ( DEBUG_PARAMETER_LISTENERS ){

	    		System.out.println( parameter + "->" + new_listeners.length );
	    	}

	    	parameterListenerz.put( parameter, new_listeners );
	    }
    }
  }

  public void addListener(COConfigurationListener listener) {
  	synchronized( listenerz ){

  		listenerz.add(listener);

  	}
  }
  public void addAndFireListener(COConfigurationListener listener) {
  	synchronized( listenerz ){

  		listenerz.add(listener);

  	}

		try{
	  	listener.configurationSaved();

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
  }
  public void removeListener(COConfigurationListener listener) {
	  synchronized( listenerz ){

  		listenerz.remove(listener);
  	}
  }

  	private boolean
  	ignoreKeyForDump(
  		String		key )
  	{
  		String	lc_key = key.toLowerCase( Locale.US );

		if ( 	key.startsWith( CryptoManager.CRYPTO_CONFIG_PREFIX ) ||
				lc_key.equals( "id" ) ||
				lc_key.equals( "azbuddy.dchat.optsmap" ) ||
				lc_key.endsWith( ".privx" ) ||
				lc_key.endsWith( ".user" ) ||
				lc_key.contains( "password" ) ||
				lc_key.contains( "username" ) ||
				lc_key.contains( "session key" )){

			return( true );
		}

		Object	value	= propertiesMap.get(key);

		if ( value instanceof byte[] ){

			try{
				value = new String((byte[])value, "UTF-8" );

			}catch( Throwable e ){

			}
		}

		if ( value instanceof String ){

			if (((String)value).toLowerCase( Locale.US ).endsWith( ".b32.i2p" )){

				return( true );
			}
		}

		return( false );
  	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Configuration Details" );

		try{
			writer.indent();

			writer.println( "version=" + Constants.BIGLYBT_VERSION + ", subver=" + Constants.SUBVERSION );

			writer.println( "System Properties" );

			try{
				writer.indent();

				Properties props = System.getProperties();

				Iterator	it = new TreeSet( props.keySet()).iterator();

				while(it.hasNext()){

					String	key = (String)it.next();

					writer.println( key + "=" + props.get( key ));
				}
			}finally{

				writer.exdent();
			}

			writer.println( "Environment" );

			try{
				writer.indent();

				Map<String,String> env = System.getenv();

				if ( env == null ){

					writer.println( "Not supported" );

				}else{

					Iterator	it = new TreeSet( env.keySet()).iterator();

					while(it.hasNext()){

						String	key = (String)it.next();

						writer.println( key + "=" + env.get( key ));
					}
				}
			}finally{

				writer.exdent();
			}

			writer.println( Constants.APP_NAME + " Config" );

			ConfigurationDefaults defaults = ConfigurationDefaults.getInstance();

			try{
				writer.indent();

				Set<String> keys =
						new TreeSet<>(
								new Comparator<String>() {
									@Override
									public int
									compare(
											String o1,
											String o2) {
										return (o1.compareToIgnoreCase(o2));
									}
								});

				keys.addAll( propertiesMap.keySet());

				Iterator<String> it = keys.iterator();

				while( it.hasNext()){

					String	key 	= it.next();

						// don't dump crypto stuff

					if ( ignoreKeyForDump( key )){

						continue;
					}

					Object	value	= propertiesMap.get(key);

					boolean bParamExists = defaults.doesParameterDefaultExist(key.toString());

					if (!bParamExists){

						key = "[NoDef] " + key;
					}else{

						Object def = defaults.getParameter( key );

						if ( def != null && value != null ){

							if ( !BEncoder.objectsAreIdentical( def, value )){

								key = "-> " + key;
							}
						}
					}

					if ( value instanceof Long ){

						writer.println( key + "=" + value );

					}else if ( value instanceof List ){

						writer.println( key + "=" + BDecoder.decodeStrings((List)BEncoder.clone(value)) + "[list]" );

					}else if ( value instanceof Map ){

						writer.println( key + "=" + BDecoder.decodeStrings((Map)BEncoder.clone(value)) + "[map]" );

					}else if ( value instanceof byte[] ){

						byte[]	b = (byte[])value;

						boolean	hex	= false;

						for (int i=0;i<b.length;i++){

							char	c = (char)b[i];

							if ( !	( 	Character.isLetterOrDigit(c) ||
										"\\ `¬\"£$%^&*()-_=+[{]};:'@#~,<.>/?'".indexOf(c) != -1 )){

								hex	= true;

								break;
							}
						}
						writer.println( key + "=" + (hex?ByteFormatter.nicePrint(b):bytesToString((byte[])value)));

					}else{

						writer.println( key + "=" + value + "[unknown]" );
					}
				}
			}finally{

				writer.exdent();
			}
		}finally{

			writer.exdent();
		}
	}

	public void
	dumpConfigChanges(
		IndentWriter	writer )
	{
		ConfigurationDefaults defaults = ConfigurationDefaults.getInstance();

		Set<String> keys =
				new TreeSet<>(
						new Comparator<String>() {
							@Override
							public int
							compare(
									String o1,
									String o2) {
								return (o1.compareToIgnoreCase(o2));
							}
						});

		keys.addAll( propertiesMap.keySet());

		Iterator<String> it = keys.iterator();

		while( it.hasNext()){

			String	key 	= it.next();

				// don't dump crypto stuff

			if ( ignoreKeyForDump( key )){

				continue;
			}

			Object	value	= propertiesMap.get(key);

			boolean bParamExists = defaults.doesParameterDefaultExist(key.toString());

			if ( bParamExists ){

				Object def = defaults.getParameter( key );

				if ( def != null && value != null ){

					if ( !BEncoder.objectsAreIdentical( def, value )){

						if ( value instanceof Long ){

							writer.println( key + "=" + value );

						}else if ( value instanceof List ){

							writer.println( key + "=" + BDecoder.decodeStrings((List)BEncoder.clone(value)) + "[list]" );

						}else if ( value instanceof Map ){

							writer.println( key + "=" + BDecoder.decodeStrings((Map)BEncoder.clone(value)) + "[map]" );

						}else if ( value instanceof byte[] ){

							byte[]	b = (byte[])value;

							boolean	hex	= false;

							for (int i=0;i<b.length;i++){

								char	c = (char)b[i];

								if ( !	( 	Character.isLetterOrDigit(c) ||
											"\\ `¬\"£$%^&*()-_=+[{]};:'@#~,<.>/?'".indexOf(c) != -1 )){

									hex	= true;

									break;
								}
							}
							writer.println( key + "=" + (hex?ByteFormatter.nicePrint(b):bytesToString((byte[])value)));

						}else{

							writer.println( key + "=" + value + "[unknown]" );
						}
					}
				}
			}
		}
	}

	protected static String
	bytesToString(
		byte[]	bytes )
	{
		try{
			return( new String( bytes, Constants.DEFAULT_ENCODING_CHARSET ));

		}catch( Throwable e ){

			return( new String(bytes));
		}
	}

	protected static byte[]
	stringToBytes(
		String	str )
	{
		if ( str == null ){

			return( null );
		}

		try{
			return( str.getBytes( Constants.DEFAULT_ENCODING_CHARSET ));

		}catch( Throwable e ){

			return( str.getBytes());
		}
	}

}
