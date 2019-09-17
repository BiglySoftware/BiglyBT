/*
 * Created on 20 mai 2004
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
package com.biglybt.ui.swt.updater2;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.swt.SWT;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.update.UpdatableComponent;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateChecker;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.pifimpl.local.PluginInitializer;

/**
 * @author Olivier Chalouhi
 *
 */
public class SWTUpdateChecker implements UpdatableComponent
{
  private static final LogIDs LOGID = LogIDs.GUI;

  public static final String	RES_EXPLICIT_FILE = "SWTUpdateChecker.explicit";

	
  public static void
  initialize()
  {
  	PluginInitializer.getDefaultInterface().getUpdateManager().registerUpdatableComponent(new SWTUpdateChecker(),true);
  }

  public SWTUpdateChecker() {
  }

  @Override
  public void 
  checkForUpdate(
		final UpdateChecker checker) 
  {
  	try{
        ResourceDownloaderFactory factory = ResourceDownloaderFactoryImpl.getSingleton();

	    SWTVersionGetter versionGetter = new SWTVersionGetter( checker );

  		String	extra = "";

  		if ( Constants.isWindows && Constants.is64Bit ){

  			extra = " (64-bit)";
  		}
	      
  		String update_name = "SWT Library for " + versionGetter.getPlatform() + extra;
  		
  		String[] update_desc = new String[] {"SWT is the graphical library used by " + Constants.APP_NAME};
  		
		UpdateCheckInstance check_inst = checker.getCheckInstance();
		
		Map<String,Object> overrides = (Map<String,Object>)check_inst.getProperty( UpdateCheckInstance.PT_RESOURCE_OVERRIDES );
		
		if ( overrides != null && overrides.containsKey( RES_EXPLICIT_FILE )){

			File file = (File)overrides.get( RES_EXPLICIT_FILE );

			ResourceDownloader rd 		= factory.create( file );

			final Update update =
					checker.addUpdate(
							update_name,
							update_desc,
							versionGetter.getCurrentVersionAndRevision(),
							"explicit",
							rd,
							Update.RESTART_REQUIRED_YES
							);

			rd.addListener(new ResourceDownloaderAdapter() {
				@Override
				public boolean
				completed(
						ResourceDownloader downloader,
						InputStream data)
				{
					//On completion, process the InputStream to store temp files

					return processData(checker,update,downloader,data);
				}

				@Override
				public void
				failed(
					ResourceDownloader			downloader,
					ResourceDownloaderException e )
				{
					Debug.out( downloader.getName() + " failed", e );

					update.complete( false );
				}
			});
			      
		}else{
	
	     	boolean	update_required  = 	System.getProperty(SystemProperties.SYSPROP_SKIP_SWTCHECK) == null && versionGetter.needsUpdate();
	
		    if ( update_required ){
	
		       	int	update_prevented_version 	= COConfigurationManager.getIntParameter( "swt.update.prevented.version", -1 );
		       	int	update_prevented_revision 	= COConfigurationManager.getIntParameter( "swt.update.prevented.revision", -1 );
	
		    	try{
			        URL	swt_url = SWT.class.getClassLoader().getResource("org/eclipse/swt/SWT.class");
	
			        if ( swt_url != null ){
	
			        	String	url_str = swt_url.toExternalForm();
	
			        	if ( url_str.startsWith("jar:file:")){
	
			        		File jar_file = FileUtil.getJarFileFromURL(url_str);
	
			        	    File	expected_dir = new File( checker.getCheckInstance().getManager().getInstallDir() );
	
			        	    File	jar_file_dir = jar_file.getParentFile();
	
			        	    	// sanity check
	
			        	    if ( expected_dir.exists() && jar_file_dir.exists() ){
	
			        	    	expected_dir	= expected_dir.getCanonicalFile();
			        	    	jar_file_dir	= jar_file_dir.getCanonicalFile();
	
	
					            if (Constants.isUnix) {
						            if ( expected_dir.equals( jar_file_dir )){
						            	// For unix, when swt.jar is in the appdir, the
							            // user put it there, so skip everything
							            return;
						            }
						            // For unix, when swt.jar is in the appdir/swt
						            expected_dir = new File(expected_dir, "swt");
					            }
	
					            if ( expected_dir.equals( jar_file_dir )){
	
			        	    			// everything looks ok
	
			        	    		if ( update_prevented_version != -1 ){
	
			        	    			update_prevented_version	= -1;
			        	    			update_prevented_revision	= -1;
			        	    			
				        	    		COConfigurationManager.setParameter( "swt.update.prevented.version", update_prevented_version );
				        	    		COConfigurationManager.setParameter( "swt.update.prevented.revision", update_prevented_revision );
			        	    		}
			        	    	}else{
	
			        	    			// we need to periodically remind the user there's a problem as they need to realise that
			        	    			// it is causing ALL updates (core/plugin) to fail
	
			        	    		String	alert =
			        	    			MessageText.getString(
			        	    					"swt.alert.cant.update",
			        	    					new String[]{
				        	    					versionGetter.getCurrentVersionAndRevision(),
				        	    					versionGetter.getLatestVersionAndRevision(),
			        	    						jar_file_dir.toString(),
			        	    						expected_dir.toString()});
	
			        	    		checker.reportProgress( alert );
	
			        	    		long	last_prompt = COConfigurationManager.getLongParameter( "swt.update.prevented.version.time", 0 );
			        	    		long	now			= SystemTime.getCurrentTime();
	
			        	    		boolean force = now < last_prompt || now - last_prompt > 7*24*60*60*1000;
	
			        	    		if ( !checker.getCheckInstance().isAutomatic()){
	
			        	    			force = true;
			        	    		}
	
			        		    	if ( 	force || 
			        		    			update_prevented_version != versionGetter.getCurrentVersion() ||
			        		    			(	update_prevented_version == versionGetter.getCurrentVersion() &&
			        		    				update_prevented_revision != versionGetter.getCurrentRevision())){
	
				        	     		Logger.log(	new LogAlert(LogAlert.REPEATABLE, LogEvent.LT_ERROR, alert ));
	
				        	     		update_prevented_version 	= versionGetter.getCurrentVersion();
				        	     		update_prevented_revision 	= versionGetter.getCurrentRevision();
	
				        	    		COConfigurationManager.setParameter( "swt.update.prevented.version", update_prevented_version );
				        	    		COConfigurationManager.setParameter( "swt.update.prevented.revision", update_prevented_revision );
				        	    		COConfigurationManager.setParameter( "swt.update.prevented.version.time", now );
			        		    	}
			        	    	}
			        	    }
			        	}
			        }
		    	}catch( Throwable e ){
	
			    	Debug.printStackTrace(e);
		    	}
	
			    if ( 	update_prevented_version == versionGetter.getCurrentVersion() &&
			    		update_prevented_revision == versionGetter.getCurrentRevision()){
	
			    	Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "SWT update aborted due to previously reported issues regarding its install location" ));
	
					checker.failed();
	
					checker.getCheckInstance().cancel();
	
					return;
			    }
	
		      String[] mirrors = versionGetter.getMirrors();
	
		      ResourceDownloader swtDownloader = null;
	
	          List<ResourceDownloader> downloaders =  new ArrayList<>();
	
	          for(int i = 0 ; i < mirrors.length ; i++) {
	            try {
	              downloaders.add(factory.getSuffixBasedDownloader(factory.create(new URL(mirrors[i]))));
	            } catch(MalformedURLException e) {
	              //Do nothing
	            	if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
											"Cannot use URL " + mirrors[i] + " (not valid)"));
	            }
	          }
	
	          for(int i = 0 ; i < mirrors.length ; i++) {
	              try {
	                downloaders.add(factory.getSuffixBasedDownloader(factory.createWithAutoPluginProxy(new URL(mirrors[i]))));
	              } catch(MalformedURLException e) {
	              }
	            }
	
	          ResourceDownloader[] resourceDownloaders =
	            (ResourceDownloader[])
	            downloaders.toArray(new ResourceDownloader[downloaders.size()]);
	
	          swtDownloader = factory.getAlternateDownloader(resourceDownloaders);
	
		      	// get the size so its cached up
	
		      try{
		      	swtDownloader.getSize();
	
		      }catch( ResourceDownloaderException e ){
	
		      	Debug.printStackTrace( e );
		      }
		
		      final Update update =
		    	  checker.addUpdate(
	    			  update_name,
	    			  update_desc,
	    			  versionGetter.getCurrentVersionAndRevision(),
	    			  versionGetter.getLatestVersionAndRevision(),
	    			  swtDownloader,
	    			  Update.RESTART_REQUIRED_YES
		          );
	
		      update.setDescriptionURL(versionGetter.getInfoURL());
	
		      swtDownloader.addListener(new ResourceDownloaderAdapter() {
	
			        @Override
			        public boolean
			        completed(
			        	ResourceDownloader downloader,
			        	InputStream data)
			        {
			        		//On completion, process the InputStream to store temp files
	
			          return processData(checker,update,downloader,data);
			        }
	
					@Override
					public void
					failed(
						ResourceDownloader			downloader,
						ResourceDownloaderException e )
					{
						Debug.out( downloader.getName() + " failed", e );
	
						update.complete( false );
					}
			      });
		    }
	    }
  	}catch( Throwable e ){
  		Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
					"SWT Version check failed", e));

  		checker.failed();

  	}finally{

  		checker.completed();
  	}

  }

  private boolean
  processData(
	UpdateChecker 		checker,
	Update				update,
	ResourceDownloader	rd,
	InputStream 		data )
  {
	ZipInputStream zip = null;

    try {
	  data = update.verifyData( data, true );

	  rd.reportActivity( "Data verified successfully" );

      UpdateInstaller installer = checker.createInstaller();

      zip = new ZipInputStream(data);

      ZipEntry entry = null;

      while((entry = zip.getNextEntry()) != null) {

        String name = entry.getName();

        	// all jars

        if ( name.endsWith( ".jar" )){

          installer.addResource(name,zip,false);

          if ( Constants.isUnix ){
          	// unix SWT goes in <appdir>/swt

            installer.addMoveAction(name,installer.getInstallDir() + File.separator + "swt" + File.separator + name);

          }else{

            installer.addMoveAction(name,installer.getInstallDir() + File.separator + name);
          }
        }else if ( name.endsWith(".jnilib") && Constants.isOSX ){

        	  //on OS X, any .jnilib

          installer.addResource(name,zip,false);

          installer.addMoveAction(name,installer.getInstallDir() + "/dll/" + name);

        }else if( name.endsWith( ".dll" ) || name.endsWith( ".so" ) ||
					name.contains(".so.")) {

           	// native stuff for windows and linux

          installer.addResource(name,zip,false);

          installer.addMoveAction(name,installer.getInstallDir() + File.separator + name);

        }else if ( name.equals("javaw.exe.manifest") || name.equals( "azureus.sig" )){

        	// silently ignore this one
        }else{

    	   Debug.outNoStack( "SWTUpdate: ignoring zip entry '" + name + "'" );
       }
      }

      update.complete( true );

    } catch(Throwable e) {

    	update.complete( false );

  		Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
				"SWT Update failed", e));
      return false;
    }finally{
    	if ( zip != null ){

    		try{
    			  zip.close();

    		}catch( Throwable e ){
    		}
    	}
    }

    return true;
  }

  @Override
  public String
  getName()
  {
    return( "SWT library" );
  }

  @Override
  public int
  getMaximumCheckTime()
  {
    return( 30 ); // !!!! TODO: fix this
  }
}
