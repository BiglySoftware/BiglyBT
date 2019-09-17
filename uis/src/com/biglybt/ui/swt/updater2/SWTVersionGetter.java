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

import java.util.Map;

import org.eclipse.swt.SWT;

import com.biglybt.core.logging.*;
import com.biglybt.core.util.AEVerifier;
import com.biglybt.core.util.Constants;

import com.biglybt.core.versioncheck.VersionCheckClient;

import com.biglybt.pif.update.UpdateChecker;
import com.biglybt.ui.swt.Utils;


/**
 * @author Olivier Chalouhi
 *
 */
public class SWTVersionGetter {
	private static final LogIDs LOGID = LogIDs.GUI;

  private String platform;
  private int currentVersion;
  private int currentRevision;
  private int latestVersion;
  private int latestRevision;
  
  private UpdateChecker	checker;

  private String[] mirrors;

	private String infoURL;

  public
  SWTVersionGetter(
  		UpdateChecker	_checker )
  {
   	platform 			= Utils.getSWTPlatform();
    currentVersion 		= Utils.getSWTVersion();
    currentRevision 	= Utils.getSWTRevision();


    latestVersion = 0;
    latestRevision = 0;
    
    checker	= _checker;
  }

  public boolean needsUpdate() {
    try {
      downloadLatestVersion();

      String msg = 
    		  "SWT: current version = " + currentVersion + (currentRevision==0?"":("r" + currentRevision)) + 
    		  ", latest version = " + latestVersion + (latestRevision==0?"":("r" + latestRevision));
      
      checker.reportProgress( msg );

      if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, msg));

      if ( latestVersion > currentVersion ){
    	  
    	  return( true );
    	  
      }else if ( latestVersion == currentVersion && latestRevision > currentRevision ){
    	  
    	  return( true );
    
      }else{
    	  
    	  return( false );
      }
    } catch(Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private void downloadLatestVersion() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Requesting latest SWT version "
					+ "and url from version check client."));

    Map reply = VersionCheckClient.getSingleton().getVersionCheckInfo(VersionCheckClient.REASON_CHECK_SWT);

    String msg = "SWT version check received:";

    boolean	done = false;

    if ( Constants.isOSX ){

	    byte[] version_bytes = (byte[])reply.get( "swt_version_cocoa" );

	    if ( version_bytes != null ){

	    	latestVersion = Integer.parseInt( new String( version_bytes ) );

	    	msg += " version=" + latestVersion;

		    byte[] revision_bytes = (byte[])reply.get( "swt_revision_cocoa" );
		    
		    if ( revision_bytes != null ) {
		    	
		      latestRevision = Integer.parseInt( new String( revision_bytes ) );
		      
		      msg += "r" + latestRevision;
		    }
	    	
    		byte[] url_bytes = (byte[])reply.get( "swt_url_cocoa" );

    		if ( url_bytes != null ){

       			mirrors = new String[] { new String( url_bytes ) };

    			msg += " url=" + mirrors[0];
    		}

    		done = true;
	    }
    }

    if ( !done ){

	    byte[] version_bytes = (byte[])reply.get( "swt_version" );
	    if( version_bytes != null ) {
	      latestVersion = Integer.parseInt( new String( version_bytes ) );
	      msg += " version=" + latestVersion;
	    }

	    byte[] revision_bytes = (byte[])reply.get( "swt_revision" );
	    if( revision_bytes != null ) {
	      latestRevision = Integer.parseInt( new String( revision_bytes ) );
	      msg += "r" + latestRevision;
	    }
	    
	    byte[] url_bytes = (byte[])reply.get( "swt_url" );
	    if( url_bytes != null ) {
	      mirrors = new String[] { new String( url_bytes ) };
	      msg += " url=" + mirrors[0];
	    }
    }

    byte[] info_bytes = (byte[])reply.get( "swt_info_url" );

    if ( info_bytes != null ){

    	byte[] sig = (byte[])reply.get( "swt_info_sig" );

		if ( sig == null ){

			Logger.log( new LogEvent( LogIDs.LOGGER, "swt info signature check failed - missing signature" ));

		}else{

		  	try{
	    		infoURL = new String( info_bytes );

	    		try{
					AEVerifier.verifyData( infoURL, sig );

				}catch( Throwable e ){

					Logger.log( new LogEvent( LogIDs.LOGGER, "swt info signature check failed", e  ));

					infoURL = null;
				}
	    	}catch ( Exception e ){

	    		Logger.log(new LogEvent(LOGID, "swt info_url", e));
	    	}
		}
    }

    if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, msg));
  }

  public String
  getLatestVersionAndRevision()
  {
	  return( latestVersion + (latestRevision==0?"":("r" + latestRevision)));
  }
  
  public int getCurrentVersion() {
	    return currentVersion;
  }
  
  public int getCurrentRevision() {
	    return currentRevision;
}
  
  public String
  getCurrentVersionAndRevision()
  {
	  return( currentVersion + (currentRevision==0?"":("r" + currentRevision)));
  }
  
  /**
   * @return Returns the platform.
   */
  public String getPlatform() {
    return platform;
  }

  /**
   * @return Returns the mirrors.
   */
  public String[] getMirrors() {
    return mirrors;
  }

	public String getInfoURL() {
		return infoURL;
	}
}
