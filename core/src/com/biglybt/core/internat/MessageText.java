/*
 * Created on 24.07.2003
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
 */
package com.biglybt.core.internat;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.Wiki;

/**
 * @author Arbeiten
 *
 * @author CrazyAlchemist Added keyExistsForDefaultLocale
 */
@SuppressWarnings("restriction")
public class MessageText {

	public static final Locale LOCALE_ENGLISH = Locale.ENGLISH;

	public static final Locale LOCALE_DEFAULT = Locale.ROOT; // == english

	private static final boolean LOG_MISSING_MESSAGES = System.getProperty("log.missing.messages", "0").equals("1");

	private static Locale LOCALE_CURRENT = LOCALE_DEFAULT;

	static final String BUNDLE_NAME;

	public static final Map<String, String> CONSTANTS = new HashMap<>();

	public static final String DEFAULT_BUNDLE_NAME = "com.biglybt.internat.MessagesBundle";

	static {
		BUNDLE_NAME = System.getProperty("az.factory.internat.bundle", DEFAULT_BUNDLE_NAME);

		updateProductName();
	}

	private static final Map pluginLocalizationPaths = new HashMap();
	private static final Collection pluginResourceBundles = new ArrayList();
	static IntegratedResourceBundle RESOURCE_BUNDLE;
	private static Set platform_specific_keys = new HashSet();
	private static final Pattern PAT_PARAM_ALPHA = Pattern.compile("(\\{([^0-9].+?)\\})");


	private static int bundle_fail_count = 0;

	private static final List listeners = new ArrayList();

	private static final String PLATFORM_SUFFIX;

	// preload default language w/o plugins
	static {
		if (Constants.isOSX) {
			PLATFORM_SUFFIX = "._mac";
		} else if (Constants.isLinux) {
			PLATFORM_SUFFIX = "._linux";
		} else if (Constants.isUnix) {
			PLATFORM_SUFFIX = "._unix";
		} else if (Constants.isFreeBSD) {
			PLATFORM_SUFFIX = "._freebsd";
		} else if (Constants.isSolaris) {
			PLATFORM_SUFFIX = "._solaris";
		} else if (Constants.isWindows) {
			PLATFORM_SUFFIX = "._windows";
		} else {
			PLATFORM_SUFFIX = "._unknown";
		}

		if (System.getProperty("SKIP_SETRB", "0").equals("0")) {
			setResourceBundle(new IntegratedResourceBundle(
					getResourceBundle(BUNDLE_NAME, LOCALE_DEFAULT,
							MessageText.class.getClassLoader()),
					pluginLocalizationPaths, null, 4000, true));
		}
  }

  	// grab a reference to the default bundle

  private static IntegratedResourceBundle DEFAULT_BUNDLE = RESOURCE_BUNDLE;

	/**
	 * Sets keys for system wide constants
	 */
  public static void
  updateProductName()
  {
	  CONSTANTS.put("base.product.name", Constants.APP_NAME);
	  CONSTANTS.put("base.wiki.url", Constants.URL_WIKI);
	  CONSTANTS.put("base.client.url", Constants.URL_CLIENT_HOME);

	  CONSTANTS.put("Alert.failed.update.url", Wiki.FAILED_UPDATE);
	  CONSTANTS.put("alltrackers.link.url", Wiki.ALL_TRACKERS_VIEW);
	  CONSTANTS.put("restart.error.url", Wiki.RESTARTING_ISSUES);
	  CONSTANTS.put("unix.script.new.manual.url", Wiki.UNIX_STARTUP_SCRIPT);
	  CONSTANTS.put("wiki.fat32", Wiki.FAT32_FILE_SIZE_LIMIT);
	  CONSTANTS.put("url.wiki.app.disappears", Wiki.APPLICATION_DISSAPEARS);
	  CONSTANTS.put("url.wiki.failed.update", Wiki.FAILED_UPDATE);
	  CONSTANTS.put("url.wiki.swt.cant.autoupdate", Wiki.SWT_CANT_AUTO_UPDATE);
	  CONSTANTS.put("faq.legal.url", Wiki.FAQ_LEGAL);	// used in rcmplugin


	  /* unused expansion keys are left out */
//	  CONSTANTS.put("ConfigView.label.general.formatters.link.url", Wiki.INTERFACE_FORMAT_OVERRIDES);
//	  CONSTANTS.put("ConfigView.section.connection.advanced.url", Wiki.ADVANCED_NETWORK_SETTINGS);
//	  CONSTANTS.put("ConfigView.section.connection.pairing.srp.url", Wiki.SECURE_REMOTE_PASSWORD); //todo: rename key
//	  CONSTANTS.put("ConfigView.section.connection.pairing.url", Wiki.PAIRING);
//	  CONSTANTS.put("ConfigView.section.dns.url", Wiki.DNS);
//	  CONSTANTS.put("MainWindow.menu.speed_limits.wiki.url", Wiki.SPEED_LIMIT_SCHEDULER);
//	  CONSTANTS.put("privacy.view.wiki.url", Wiki.PRIVACY_VIEW);
//	  CONSTANTS.put("device.wiki.itunes", Wiki.DEVICES_ITUNES_TIPS);

  }

  public static void loadBundle() {
	  loadBundle(false);
  }

  public static void loadBundle(boolean forceReload) {
	  Locale	old_locale = getCurrentLocale();

		String savedLocaleString = COConfigurationManager.getStringParameter("locale");
		Locale savedLocale = parseFormattedLocaleString(savedLocaleString);

		if (!Locale.ROOT.equals(savedLocale)) {
			MessageText.changeLocale(savedLocale, forceReload);
		}

		COConfigurationManager
				.setParameter("locale.set.complete.count", COConfigurationManager
						.getIntParameter("locale.set.complete.count") + 1);

		Locale	new_locale = getCurrentLocale();

		if ( !old_locale.equals( new_locale ) || forceReload){

			for (int i=0;i<listeners.size();i++){

				try{
					((MessageTextListener)listeners.get(i)).localeChanged( old_locale, new_locale);

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

  	public static void
  	addListener(
  		MessageTextListener	listener )
  	{
  		listeners.add( listener );
  	}
 	public static void
  	addAndFireListener(
  		MessageTextListener	listener )
  	{
  		listeners.add( listener );

  		listener.localeChanged( getCurrentLocale(), getCurrentLocale());
  	}

  	public static void
  	removeListener(
  		MessageTextListener	listener )
  	{
  		listeners.remove( listener );
  	}

  static ResourceBundle
  getResourceBundle(
	String		name,
	Locale		loc,
	ClassLoader	cl )
  {
	  try{

		 return( ResourceBundle.getBundle(name, loc, cl ));

	  }catch( Throwable e ){

		  bundle_fail_count++;

		  if ( bundle_fail_count == 1 ){

			  e.printStackTrace();

			  Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
						"Failed to load resource bundle. One possible cause is "
								+ "that you have installed " + Constants.APP_NAME + " into a directory "
								+ "with a '!' in it. If so, please remove the '!'."));
		  }

		  return(
			  new ResourceBundle()
			  {
				  @Override
				  public Locale
				  getLocale()
				  {
					return( LOCALE_DEFAULT );
				  }

				  @Override
				  protected Object
				  handleGetObject(String key)
				  {
						return( null );
				  }

				  @Override
				  public Enumeration
				  getKeys()
				  {
					return( new Vector().elements());
				  }
			  });
	  }
  }

  private static void
  setResourceBundle(
	  IntegratedResourceBundle	bundle )
  {
	  RESOURCE_BUNDLE	= bundle;

	  Iterator	keys = RESOURCE_BUNDLE.getKeysLight();

		String ui_suffix = getUISuffix();

	  Set platformKeys = new HashSet();

	  while( keys.hasNext()){
		  String	key = (String)keys.next();
		  if ( key.endsWith( PLATFORM_SUFFIX ))
			  platformKeys.add( key );
		  else if ( key.endsWith(ui_suffix )) {
				RESOURCE_BUNDLE.addString(
						key.substring(0, key.length() - ui_suffix.length()),
						RESOURCE_BUNDLE.getString(key));
		  }
	  }

	  platform_specific_keys = platformKeys;
  }


  public static boolean keyExists(String key) {
    try {
    	getResourceBundleString(key);
      return true;
    } catch (MissingResourceException e) {
      return false;
    }
  }

    public static boolean keyExistsForDefaultLocale(final String key) {
        try {
            DEFAULT_BUNDLE.getString(key);
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }


  /**
   * @param key
   * @return
   */
  public static String
  getString(
	String key,
	String sDefault)
  {
	  if (key == null || key.length() == 0) {
		  return "";
	  }

	  if (key.startsWith("!") && key.endsWith("!")) {
		  return expandValue(key.substring(1, key.length() - 1));
	  }

	  String	target_key = key + PLATFORM_SUFFIX;

	  if ( !platform_specific_keys.contains( target_key )){

		  target_key	= key;
	  }

	  try {

		  return getResourceBundleString( target_key );

	  }catch (MissingResourceException e) {

		  return getPlatformNeutralString(key, sDefault);
	  }
  }

  public static String
  getString(
		  String key)
  {
  	if (key == null || key.length() == 0) {
		  return "";
	  }

		if (key.startsWith("!") && key.endsWith("!")) {
			return expandValue(key.substring(1, key.length() - 1));
		}

	  String	target_key = key + PLATFORM_SUFFIX;

	  if ( !platform_specific_keys.contains( target_key )){

		  target_key	= key;
	  }

	  try {

		  return getResourceBundleString( target_key );

	  } catch (MissingResourceException e) {

	      return getPlatformNeutralString(key);
	  }
  }

  public static String getPlatformNeutralString(String key) {
    try {
      return getResourceBundleString(key);
    } catch (MissingResourceException e) {
    	// we support the usage of non-resource strings as long as they are wrapped in !
      if ( key.startsWith("!") && key.endsWith( "!" )){
    	  return( expandValue(key.substring(1,key.length()-1 )));
      }
			if (LOG_MISSING_MESSAGES) {
				System.err.println("Missing message key '" + key + "' via " + Debug.getCompressedStackTraceSkipFrames(2));
			}
      return '!' + key + '!';
    }
  }

  public static String getPlatformNeutralString(String key, String sDefault) {
    try {
      return getResourceBundleString(key);
    } catch (MissingResourceException e) {
    	if ( key.startsWith("!") && key.endsWith( "!" )){
      	  return( expandValue(key.substring(1,key.length()-1 )));
        }
      return sDefault;
    }
  }

  private static String getResourceBundleString(String key) {
  	if (key == null) {
  		return "";
  	}

	  String value;

  	Object defaultValue = CONSTANTS.get(key);
  	if (defaultValue != null) {
  		value = String.valueOf(defaultValue);
	  } else {
  		value = RESOURCE_BUNDLE.getString(key);
	  }

		return expandValue(value);
	}

  public static String expandValue(String value) {
	  // Replace {*} with a lookup of *
	  if (value != null && value.indexOf('}') > 0) {
		  Matcher matcher = PAT_PARAM_ALPHA.matcher(value);
		  while (matcher.find()) {
			  String key = matcher.group(2);
			  String expression = matcher.group(1);
			  String text = null;

			  try {
				  String	target_key = key + PLATFORM_SUFFIX;

				  if ( !platform_specific_keys.contains( target_key )){

					  target_key	= key;
				  }
				  text = getResourceBundleString(target_key);
			  } catch (MissingResourceException ignore) {
				  // no substitution for particular key
			  }

			  if (text != null) {
					value = value.replace(expression, text);
				}
		  }
	  }
	  return value;
  }

	private static String getUISuffix() {
		return "az2".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"))
				? "._classic" : "._vuze";
	}

  /**
   * Expands a message text and replaces occurrences of %1 with first param, %2 with second...
   * @param key
   * @param params
   * @return
   */
  public static String
  getString(
  		String		key,
		String[] params )
  {
	  String	res = getString(key, (String) null);

	  if (res == null) {
		  if (LOG_MISSING_MESSAGES) {
			  System.err.println("Missing message key '" + key + "', params " + Arrays.toString(params));
		  }

		  if (params == null || params.length == 0 ) {
			  return  "!" + key + "!";
		  }
		  return "!" + key + "(" + Arrays.toString(params) + ")" + "!";
	  }

	  if (params == null || params.length == 0 ) {
		  return res;
	  }

	  for(int i=0;i<params.length;i++){

		  String	from_str 	= "%" + (i+1);
		  String	to_str		= params[i];

		  if ( to_str == null ){

			  to_str = "<null>";
		  }

		  to_str = to_str.replace( '%', '\uFDE5' );	// invalid char to avoid %n in to_str being expanded

		  res = replaceStrings( res, from_str, to_str );
	  }

	  return( res.replace( '\uFDE5', '%' ));
  }

  protected static String
  replaceStrings(
  	String	str,
	String	f_s,
	String	t_s )
  {
  	int	pos = 0;

  	String	res  = "";

  	while( pos < str.length()){

  		int	p1 = str.indexOf( f_s, pos );

  		if ( p1 == -1 ){

  			res += str.substring(pos);

  			break;
  		}

  		res += str.substring(pos, p1) + t_s;

  		pos = p1+f_s.length();
  	}

  	return( res );
  }

  public static String getDefaultLocaleString(String key) {
    // TODO Auto-generated method stub
    try {
      return DEFAULT_BUNDLE.getString(key);
    } catch (MissingResourceException e) {
    	// we support the usage of non-resource strings as long as they are wrapped in !
    	if ( key.startsWith("!") && key.endsWith( "!" )){
    		return( key.substring(1,key.length()-1 ));
    	}
      return '!' + key + '!';
    }
  }

  public static Locale getCurrentLocale() {
    return LOCALE_DEFAULT.equals(LOCALE_CURRENT) ? LOCALE_ENGLISH : LOCALE_CURRENT;
  }

  public static boolean isCurrentLocale(Locale locale) {
    return LOCALE_ENGLISH.equals(locale) ? LOCALE_CURRENT.equals(LOCALE_DEFAULT) : LOCALE_CURRENT.equals(locale);
  }

  public static Locale[] getLocales(boolean sort) {
    String bundleFolder = BUNDLE_NAME.replace('.', '/');
    final String prefix = BUNDLE_NAME.substring(BUNDLE_NAME.lastIndexOf('.') + 1);
    final String extension = ".properties";

    String urlString = MessageText.class.getClassLoader().getResource(bundleFolder.concat(extension)).toExternalForm();
    //System.out.println("urlString: " + urlString);
    String[] bundles = null;

    if (urlString.startsWith("jar:file:")) {

			File jar = FileUtil.getJarFileFromURL(urlString);

			if (jar != null) {
				try {
					// System.out.println("jar: " + jar.getAbsolutePath());
					try (JarFile jarFile = new JarFile(jar) ){
						Enumeration entries = jarFile.entries();
						ArrayList<String> list = new ArrayList<>(250);
						while (entries.hasMoreElements()) {
							JarEntry jarEntry = (JarEntry) entries.nextElement();
							if (jarEntry.getName().startsWith(bundleFolder)
									&& jarEntry.getName().endsWith(extension)) {
								// System.out.println("jarEntry: " + jarEntry.getName());
								list.add(jarEntry.getName().substring(
										bundleFolder.length() - prefix.length()));
								// "MessagesBundle_de_DE.properties"
							}
						}
						bundles = list.toArray(new String[0]);
					}
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
			}
		} else {
      File bundleDirectory = FileUtil.newFile(URI.create(urlString)).getParentFile();
      //      System.out.println("bundleDirectory: " +
      // bundleDirectory.getAbsolutePath());

      bundles = bundleDirectory.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.startsWith(prefix) && name.endsWith(extension);
        }
      });
    }

    HashSet<String> bundleSet = new HashSet<>();

    // Add local first
    File localDir = FileUtil.newFile(SystemProperties.getUserPath());
    String[] localBundles = localDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(prefix) && name.endsWith(extension);
      }
    });

    	// can be null if user path is borked

    if ( localBundles != null ){
    	Collections.addAll(bundleSet, localBundles);
    }

    // Add AppDir 2nd
    File appDir = FileUtil.newFile(SystemProperties.getApplicationPath());
    String[] appBundles = appDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(prefix) && name.endsWith(extension);
      }
    });

    	// can be null if app path is borked
    if ( appBundles != null ){
    	Collections.addAll(bundleSet, appBundles);
    }
    // Any duplicates will be ignored
		Collections.addAll(bundleSet, bundles);

    List<Locale> foundLocalesList = new ArrayList<>(bundleSet.size());

  	foundLocalesList.add( LOCALE_ENGLISH );

		for (String sBundle : bundleSet) {
			if (prefix.length() + 1 < sBundle.length() - extension.length()) {
				String locale = sBundle.substring(prefix.length() + 1, sBundle.length() - extension.length());
				Locale parsedLocale = parseFormattedLocaleString(locale);
				if (!Locale.ROOT.equals(parsedLocale)) {
					//System.out.println("Locale: " + locale);
					foundLocalesList.add(parsedLocale);
				}
			}
		}

		Locale[] foundLocales = foundLocalesList.toArray(new Locale[0]);

    if (sort) {
      try{
  	    Arrays.sort(foundLocales, new Comparator<Locale>() {
  	      @Override
	        public final int compare (Locale a, Locale b) {
  	    	  a = getDisplaySubstitute(a);
  	    	  b = getDisplaySubstitute(b);
  	        
  	    	  return(a.getDisplayName(a).compareToIgnoreCase(b.getDisplayName(b)));
  	      }
  	    });
      }catch( Throwable e ){
      	// user has a problem whereby a null-pointer exception occurs when sorting the
      	// list - I've done some fixes to the locale list construction but am
      	// putting this in here just in case
      	Debug.printStackTrace( e );
      }
    }
    return foundLocales;
  }

  public static boolean changeLocale(Locale newLocale) {
    return changeLocale(newLocale, false);
  }

  private static boolean changeLocale(Locale newLocale, boolean force) {
	// set locale for startup (will override immediately it on locale change anyway)
	Locale.setDefault(newLocale);

    if (!isCurrentLocale(newLocale) || force) {
      Locale.setDefault(LOCALE_DEFAULT);
      ResourceBundle newResourceBundle = null;
      String bundleFolder = BUNDLE_NAME.replace('.', '/');
      final String prefix = BUNDLE_NAME.substring(BUNDLE_NAME.lastIndexOf('.') + 1);
      final String extension = ".properties";

      if(newLocale.equals(LOCALE_ENGLISH))
    	  newLocale = LOCALE_DEFAULT;

      try {
        File userBundleFile = FileUtil.newFile(SystemProperties.getUserPath());
        File appBundleFile = FileUtil.newFile(SystemProperties.getApplicationPath());

        // Get the jarURL
        // XXX Is there a better way to get the JAR name?
        ClassLoader cl = MessageText.class.getClassLoader();

        URL u = cl.getResource(bundleFolder + extension);

        if ( u == null ){

        		// might be missing entirely

        	return( false );
        }
        String sJar = u.toString();
        sJar = sJar.substring(0, sJar.length() - prefix.length() - extension.length());
        URL jarURL = new URL(sJar);

        // User dir overrides app dir which overrides jar file bundles
        URL[] urls = {userBundleFile.toURI().toURL(), appBundleFile.toURI().toURL(), jarURL};

        /* This is debugging code, use it when things go wrong :) The line number
         * is approximate as the input stream is buffered by the reader...

        {
        	LineNumberInputStream lnis	= null;

            try{
                ClassLoader fff = new URLClassLoader(urls);

                java.io.InputStream stream = fff.getResourceAsStream("MessagesBundle_th_TH.properties");

                lnis = new LineNumberInputStream( stream );

                new java.util.PropertyResourceBundle(lnis);
            }catch( Throwable e ){

            	System.out.println( lnis.getLineNumber());

            	e.printStackTrace();
            }
        }
        */

        newResourceBundle = getResourceBundle("MessagesBundle", newLocale,
                                                      new URLClassLoader(urls));
        // do more searches if getBundle failed, or if the language is not the
        // same and the user wanted a specific country
        if ((!newResourceBundle.getLocale().getLanguage().equals(newLocale.getLanguage()) &&
             !newLocale.getCountry().equals(""))) {

          Locale foundLocale = newResourceBundle.getLocale();
          System.out.println("changeLocale: "+
                             (foundLocale.toString().equals("") ? "*Default Language*" : foundLocale.getDisplayLanguage()) +
                             " != "+newLocale.getDisplayName()+". Searching without country..");
          // try it without the country
          Locale localeJustLang = new Locale(newLocale.getLanguage());
          newResourceBundle = getResourceBundle("MessagesBundle", localeJustLang,
                                                        new URLClassLoader(urls));

          if (newResourceBundle == null ||
              !newResourceBundle.getLocale().getLanguage().equals(localeJustLang.getLanguage())) {
            // find first language we have in our list
            System.out.println("changeLocale: Searching for language " + newLocale.getDisplayLanguage() + " in *any* country..");
            Locale[] locales = getLocales(false);
            for (int i = 0; i < locales.length; i++) {
              if (locales[i].getLanguage().equals( newLocale.getLanguage())) {
                newResourceBundle = getResourceBundle("MessagesBundle", locales[i],
                                                              new URLClassLoader(urls));
                break;
              }
            }
          }
        }
      } catch (MissingResourceException e) {
        System.out.println("changeLocale: no resource bundle for " + newLocale);
        Debug.printStackTrace( e );
        return false;
      } catch (Exception e) {
      	Debug.printStackTrace( e );
      }

      if (newResourceBundle != null)
			{
				if (!newLocale.equals(LOCALE_DEFAULT) && !newResourceBundle.getLocale().equals(newLocale))
				{
					String sNewLanguage = newResourceBundle.getLocale().getDisplayName();
					if (sNewLanguage == null || sNewLanguage.trim().equals(""))
						sNewLanguage = "English (default)";
					System.out.println("changeLocale: no message properties for Locale '" + newLocale.getDisplayName() + "' (" + newLocale + "), using '" + sNewLanguage + "'");
					if (newResourceBundle.getLocale().equals(RESOURCE_BUNDLE.getLocale())) {
						return false;
					}
				}
				newLocale = newResourceBundle.getLocale();
				Locale.setDefault(newLocale.equals(LOCALE_DEFAULT) ? LOCALE_ENGLISH : newLocale);
				LOCALE_CURRENT = newLocale;
				setResourceBundle(new IntegratedResourceBundle(newResourceBundle, pluginLocalizationPaths, null, 4000, true ));
				if(newLocale.equals(LOCALE_DEFAULT))
					DEFAULT_BUNDLE = RESOURCE_BUNDLE;
				return true;
			} else
				return false;
    }
    return false;
  }

  // TODO: This is slow. For every call, IntegratedResourceBundle creates
  //       a hashtables and fills it with the old resourceBundle, then adds
  //       the new one, and then puts it all back into a ListResourceBundle.
  //       As we get more plugins, the time to add a new plugin's language file
  //       increases dramatically (even if the language file only has 1 entry!)
  //       Fix this by:
  //         - Create only one IntegratedResourceBundle
  //         - extending ResourceBundle
  //         - override handleGetObject, store in hashtable
  //         - function to add another ResourceBundle, adds to hashtable
  public static boolean integratePluginMessages(String localizationPath, ClassLoader classLoader) {
		boolean integratedSuccessfully = false;

			// allow replacement of localisation paths so that updates of unloadable plugins
			// replace messages

		if ( localizationPath != null && localizationPath.length() != 0 ){

			synchronized (pluginLocalizationPaths)
			{
				pluginLocalizationPaths.put(localizationPath, classLoader);
			}

			RESOURCE_BUNDLE.addPluginBundle(localizationPath, classLoader);
			setResourceBundle(RESOURCE_BUNDLE);

			integratedSuccessfully = true;
		}
		return integratedSuccessfully;
	}

  public static boolean integratePluginMessages(ResourceBundle bundle) {
		synchronized (pluginResourceBundles)
		{
			pluginResourceBundles.add(bundle);
		}

		RESOURCE_BUNDLE.addResourceMessages(bundle,true);
		setResourceBundle(RESOURCE_BUNDLE);

		return true;
	}


  private static final Map<String,Locale> substitutes = new HashMap<>();
  
  static{
	  if ( new Locale( "vls", "BE" ).getDisplayLanguage().equals( "vls" )){
	  
		  substitutes.put( "vls_BE", new Locale( "nl", "BE" ));
	  }
  }
  
  public static Locale
  getDisplaySubstitute(
	Locale		l )
  {
	  if ( substitutes.isEmpty()){
		  
		  return( l );
	  }
	  
	  Locale res =  substitutes.get( l.getLanguage() + "_" +  l.getCountry());
	
	  if ( res != null ){
		  
		  return( res );
		  
	  }else{
		
		return( l );
	}
  }
  
  public static interface
  MessageTextListener
  {
	  public void
	  localeChanged(
		Locale	old_locale,
		Locale	new_locale );
  }

	/**
	 * @return matching Locale or {@link Locale#ROOT} if unrecognized format or imparsable.
	 */
	static Locale parseFormattedLocaleString(String savedLocaleString) {
		Locale.Builder localeBuilder = new Locale.Builder();
		String[] savedLocaleStrings = savedLocaleString.split("_", 3);

		if (savedLocaleStrings.length < 1 || savedLocaleStrings.length > 3) {
			return Locale.ROOT;
		}

		localeBuilder.setLanguage(savedLocaleStrings[0]);

		if (savedLocaleStrings.length == 1) {
			return localeBuilder.build(); //language code only
		}

		boolean hasScript = savedLocaleStrings[1].length() == 4;
		if (hasScript) {
			localeBuilder.setScript(savedLocaleStrings[1]);
		} else {
			localeBuilder.setRegion(savedLocaleStrings[1]);
			if (savedLocaleStrings.length == 3) {
				try{
					// Java support for IETF BCP 47 causes this to throw exceptions if the variant doesn't follow
					// certain restrictions. On OSX (at least) Locale.getDefault() returns zh_CN_Hans for simplified
					// chinese OS locale, the "Hans" is malformed wrt BCP 47 and causes an init fail. Could be that
					// the bug was seen when a Java 1.8 run set "locale" and then the JVM was updated to cause this error
					
					localeBuilder.setVariant(savedLocaleStrings[2]);
					
				}catch( Throwable e ){
					
				}
			}
		}

		return localeBuilder.build();
	}

	/**
	 * Supplier of localized string for given key.
	 * The string construction is lazy evaluated.
	 */
	public static StringSupplier getStringProvider(
			final String key,
			final String... params) {
		return new StringSupplier()
		{
			@Override
			public String get() {
				return getString(key, params);
			}

			@Override
			public String toString() {
				return "key=" + key; //debug view
			}
		};
	}

}
