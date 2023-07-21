/*
 * Created on May 1, 2007
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


package com.biglybt.core.networkmanager.admin.impl;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.*;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.PluginConfigImpl;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.plugin.upnp.UPnPPlugin;

public class
NetworkAdminSpeedTestScheduledTestImpl
	implements NetworkAdminSpeedTestScheduledTest
{
	//Types of requests sent to SpeedTest scheduler.
    private static final long REQUEST_TEST 		= 0;
    private static final long CHALLENGE_REPLY 	= 1;
    private static final long TEST_RESULT		= 2;

    private static final int ZERO_DOWNLOAD_SETTING = -1;


	final PluginInterface						plugin;
  final NetworkAdminSpeedTesterImpl			tester;

    private String detectedRouter;

	private SpeedTestDownloadState		preTestSettings;

	private byte[]		challenge_id;
	long		delay_millis;
	long		max_speed;
	TOTorrent	test_torrent;

	volatile boolean	aborted;

	private final CopyOnWriteList		listeners = new CopyOnWriteList();

	protected
	NetworkAdminSpeedTestScheduledTestImpl(
		PluginInterface						_plugin,
		NetworkAdminSpeedTesterImpl			_tester )
	{
		plugin		= _plugin;
		tester		= _tester;

        	//detect the router.

        PluginInterface upnp = plugin.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );

        if( upnp != null ){

            detectedRouter = upnp.getPluginconfig().getPluginStringParameter("plugin.info");
        }

		tester.addListener(
			new NetworkAdminSpeedTesterListener()
			{
				@Override
				public void
				complete(
					NetworkAdminSpeedTester 		tester,
					NetworkAdminSpeedTesterResult 	result )
				{
					try{
						sendResult( result );

					}finally{

						reportComplete();
					}
				}

				@Override
				public void
				stage(
					NetworkAdminSpeedTester 	tester,
					String 						step )
				{
				}
			});
	}

	@Override
	public NetworkAdminSpeedTester
	getTester()
	{
		return( tester );
	}

	@Override
	public long
	getMaxUpBytePerSec()
	{
		return( max_speed );
	}

	@Override
	public long
	getMaxDownBytePerSec()
	{
		return( max_speed );
	}

	@Override
	public boolean
	start()
	{
		if ( schedule()){

			new AEThread( "NetworkAdminSpeedTestScheduledTest:delay", true )
			{
				@Override
				public void
				runSupport()
				{
					long delay_ticks = delay_millis/1000;

					for (int i=0;i<delay_ticks;i++){

						if ( aborted ){

			            	break;
			            }

                        String testScheduledIn = MessageText.getString( "SpeedTestWizard.abort.message.scheduled.in"
                                , new String[]{""+(delay_ticks - i)} );
						reportStage( testScheduledIn );

						try{
							Thread.sleep(1000);

						}catch( InterruptedException e ){

							e.printStackTrace();
						}
					}

					if ( !aborted ){

						setSpeedLimits();

						if ( tester.getTestType() == NetworkAdminSpeedTestScheduler.TEST_TYPE_BT ){

							((NetworkAdminSpeedTesterBTImpl)tester).start( test_torrent );

						}else{
							String unsupportedType = MessageText.getString("SpeedTestWizard.abort.message.unsupported.type");
							tester.abort(unsupportedType);
						}
					}
				}
			}.start();

			return( true );

		}else{

			return( false );
		}
	}

	@Override
	public void
	abort()
	{
        abort( MessageText.getString("SpeedTestWizard.abort.message.manual.abort") );
	}

	public void
	abort(
		String	reason )
	{
		if ( !aborted ){

			aborted	= true;

			tester.abort( reason );
		}
	}

	/**
     * Request a test from the speed testing service, handle the "challenge" if request and then get
     * the id for the test.
     *
     * Per spec all request are BEncoded maps.
     *
     * @return boolean - true if the test has been reserved with the service.
     */
    private boolean
    schedule()
    {
        try{
            //lookup UPnP devices found. One might be a router.

            //Send "schedule test" request.
            Map request = new HashMap();
            request.put("request_type", new Long(REQUEST_TEST) );

            String id = COConfigurationManager.getStringParameter("ID","unknown");

            	// get jar file its version for the test

            File	jar_file 		= null;
            String	jar_version		= null;

            String	explicit_path = System.getProperty(SystemProperties.SYSPROP_SPEED_TEST_CHALLENGE_JAR_PATH, null );

            if ( explicit_path != null ){

            	File	f = new File( explicit_path );

            	if ( f.exists()){

            		String v = getVersionFromJAR( f );

            		if ( v != null ){

             			jar_file	= f;
            			jar_version	= v;

               			System.out.println( "SpeedTest: using explicit challenge jar " + jar_file.getAbsolutePath() + ", version " + jar_version );
            		}
            	}
            }

            if ( jar_file == null ){

                String debug = System.getProperty("debug.speed.test.challenge","n");

                if( !debug.equals( "n" )){

                	//over-ride the jar version, and location for debugging.

                	File f = new File( "C:\\test\\azureus\\Azureus3.0.1.2.jar" );  //ToDo: make this a -D option with this default.

                	if ( f.exists()){

                		jar_file 	= f;
                		jar_version = "3.0.1.2";

              			System.out.println( "SpeedTest: using old spec challenge jar " + jar_file.getAbsolutePath() + ", version " + jar_version );
                	}
                }
            }

            if ( jar_file == null ){

            	jar_file = FileUtil.getJarFileFromClass( getClass());

            	if ( jar_file != null ){

            		jar_version = Constants.BIGLYBT_VERSION;

          			// System.out.println( "SpeedTest: using class-based challenge jar " + jar_file.getAbsolutePath() + ", version " + jar_version );

            	}else{

            		File f = new File( SystemProperties.getAzureusJarPath());

            		if ( f.exists()){

            			jar_version = Constants.BIGLYBT_VERSION;
            			jar_file	= f;

             			// System.out.println( "SpeedTest: using config-based challenge jar " + jar_file.getAbsolutePath() + ", version " + jar_version );
            		}
            	}
            }

            if ( jar_file == null ){

            	throw( new Exception( "Failed to locate a jar to use for the challenge protocol" ));
            }

            //ToDo: remove once challenge testing is done.

            request.put("az-id",id); //Where to I get the AZ-ID and client version from the Configuration?
            request.put("type","both");
            request.put("jar_ver",jar_version);

            if ( detectedRouter != null ){

            	request.put( "router", detectedRouter );
            }

            Map result = sendRequest( request );

            challenge_id = (byte[]) result.get("challenge_id");

            if( challenge_id == null ){

                throw new IllegalStateException("No challenge returned from speed test scheduling service");
            }

            Long responseType =  (Long) result.get("reply_type");

            if( responseType.intValue()==1 ){
                	//a challenge has occured.
                result = handleChallengeFromSpeedTestService( jar_file, result );

                responseType = (Long) result.get("reply_type");
            }

            if( responseType == null ){
                throw new IllegalStateException("No challenge response returned from speed test scheduling service");
            }

            if( responseType.intValue()==0 ){
                	//a test has been scheduled.
                	//set the Map properly.

                Long time = (Long) result.get("time");
                Long limit = (Long) result.get("limit");

                if( time==null || limit==null ){
                    throw new IllegalArgumentException("Returned time or limit parameter is null");
                }

                delay_millis 	= time.longValue();
                max_speed		= limit.longValue();

                	// this is test-specific data

                Map torrentMap = (Map)result.get("torrent");

                test_torrent = TOTorrentFactory.deserialiseFromMap(torrentMap);

                return( true );
            }else{

                throw new IllegalStateException( "Unrecognized response from speed test scheduling service." );
            }

        }catch( Throwable t ){

            Debug.printStackTrace(t);

            tester.abort( MessageText.getString("SpeedTestWizard.abort.message.scheduling.failed"), t );

            return( false );
        }
    }

    private String
    getVersionFromJAR(
    	File	jar_file )
    {
		try{
				// force the URLClassLoader to load from the URL and not delegate and find the currently
				// jar's Constants

			ClassLoader parent = new ClassLoader()
			{
				@Override
				protected synchronized Class
				loadClass(
					String 		name,
					boolean		resolve )

					throws ClassNotFoundException
				{
					if ( name.equals( "com.biglybt.core.util.Constants")){

						throw( new ClassNotFoundException());
					}

					return( super.loadClass( name, resolve ));
				}
			};

			ClassLoader cl = new URLClassLoader(new URL[]{jar_file.toURI().toURL()}, parent);

	    	Class c = cl.loadClass("com.biglybt.core.util.Constants");

	    	Field	field = c.getField( "AZUREUS_VERSION" );

	    	return((String)field.get( null ));

		}catch( Throwable e){

			return( null );
		}
    }


    /**
     *
     * @param jar_file - File Azureus jar used to load classes.
     * @param result - Map from the previous response
     * @return Map - from the current response.
     */
    private Map
    handleChallengeFromSpeedTestService(
    	File		jar_file,
    	Map 		result )

    	throws IOException
    {
        //verify the following items are in the response.

        //size (in bytes)
        //offset (in bytes)
        //challenge_id
        Map retVal = new HashMap();
        RandomAccessFile raf=null;
        try{

            Long size = (Long) result.get("size");
            Long offset = (Long) result.get("offset");

            if( size==null || offset==null  )
                throw new IllegalStateException("scheduleTestWithSpeedTestService had a null parameter.");


            //read the bytes
            raf = new RandomAccessFile( jar_file, "r" );
            byte[] jarBytes = new byte[size.intValue()];

            raf.seek(offset.intValue());
            raf.read( jarBytes );


            //Build the URL.
            Map request = new HashMap();
            request.put("request_type", new Long(CHALLENGE_REPLY) );
            request.put("challenge_id", challenge_id );
            request.put("data",jarBytes);

            retVal = sendRequest( request );

        }finally{
            //close
            try{
                if(raf!=null)
                    raf.close();
            }catch(Throwable t){
                Debug.printStackTrace(t);
            }
        }

        return retVal;
    }


    void
  	sendResult(
  		NetworkAdminSpeedTesterResult	result )
    {
    	try{
    		if ( challenge_id != null ){

	    		Map request = new HashMap();

	    		request.put("request_type", new Long(TEST_RESULT) );

	    		request.put("challenge_id", challenge_id );

    			request.put( "type", new Long( tester.getTestType()));
    			request.put( "mode", new Long( tester.getMode()));
    			request.put( "crypto", new Long( tester.getUseCrypto()?1:0));

	    		if ( result.hadError()){

	    			request.put( "result", new Long(0));

	    			request.put( "error", result.getLastError());

	    		}else{

	    			request.put( "result", new Long(1));

	    			request.put( "maxup", new Long(result.getUploadSpeed()));
	    			request.put( "maxdown", new Long(result.getDownloadSpeed()));
	    		}

	    		sendRequest( request );
    		}
    	}catch( Throwable e ){

    		Debug.printStackTrace(e);
    	}
    }

    private Map
    sendRequest(
    	Map		request )

    	throws IOException
    {
        request.put( "ver", new Long(1) );//request version
        request.put( "locale",  MessageText.getCurrentLocale().toString());

        String speedTestServiceName = System.getProperty( "speedtest.service.ip.address", Constants.SPEED_TEST_SERVER );

        URL urlRequestTest = new URL("http://"+speedTestServiceName+":60000/scheduletest?request="
                + URLEncoder.encode( new String(BEncoder.encode(request),"ISO-8859-1"),"ISO-8859-1"));

        return( getBEncodedMapFromRequest( urlRequestTest ));

    }
    /**
     * Read from URL and return byte array.
     * @param url -
     * @return byte[] of the results. Max size currently 100k.
     * @throws java.io.IOException -
     */
    private Map getBEncodedMapFromRequest(URL url)
            throws IOException
    {

        ResourceDownloader rd = ResourceDownloaderFactoryImpl.getSingleton().create( url );

        InputStream is=null;
        Map reply = new HashMap();
        try
        {
            is = rd.download();
            reply = BDecoder.decode( new BufferedInputStream(is) );

            //all replys of this type contains a "result"
            Long res = (Long) reply.get("result");
            if(res==null)
                throw new IllegalStateException("No result parameter in the response!! reply="+reply);
            if(res.intValue()==0){
                StringBuilder msg = new StringBuilder("Server failed. ");
                String error = new String( (byte[]) reply.get("error") );
                String errDetail = new String( (byte[]) reply.get("error_detail") );
                msg.append("Error: ").append(error);

                // detail is of no interest to the user
                // msg.append(" ,error detail: ").append(errDetail);

                Debug.outNoStack( "SpeedCheck server returned an error: " + error + ", details=" + errDetail );

                throw new IOException( msg.toString() );
            }
        }catch(IOException ise){
            //rethrow this type of exception.
            throw ise;
        }catch(Throwable t){
            Debug.out(t);
            Debug.printStackTrace(t);
        }finally{
            try{
                if(is!=null)
                    is.close();
            }catch(Throwable e){
                Debug.printStackTrace(e);
            }
        }
        return reply;
    }//getBytesFromRequest


    /**
     * Restore all the downloads the state before the speed test started.
     */
    protected synchronized void
    resetSpeedLimits()
    {
    	if ( preTestSettings != null ){

	        preTestSettings.restoreLimits();

	        preTestSettings = null;
    	}
    }

    /**
     * Preserve all the data about the downloads while the test is running.
     */
    protected synchronized void setSpeedLimits(){

    		// in case we've already saved limits

    	resetSpeedLimits();

        preTestSettings = new SpeedTestDownloadState();

        preTestSettings.saveLimits();
      }


    // ---------------    HELPER CLASSES BELOW HERE   ---------------- //

    /**
     * Preservers the state of all the downloads before the speed test started.
     */
    class
    SpeedTestDownloadState
    	implements ParameterListener, DownloadManagerListener
    {

        private final Map torrentLimits = new HashMap(); //Map <Download , Map<String,Integer> >

        public static final String TORRENT_UPLOAD_LIMIT 	= "u";
        public static final String TORRENT_DOWNLOAD_LIMIT 	= "d";

        	//global limits.

        int maxUploadKbs;
        int maxUploadSeedingKbs;
        int maxDownloadKbs;

        boolean autoSpeedEnabled;
        boolean autoSpeedSeedingEnabled;
        boolean LANSpeedEnabled;

        public
        SpeedTestDownloadState()
        {
        }

        @Override
        public void
        parameterChanged(
        	String name )
        {
        		// add some trace so we have some clue as to what has made the change!

        	String trace = Debug.getCompressedStackTrace();

        	abort( "Configuration parameter '" + name + "' changed (new value=" + COConfigurationManager.getParameter( name ) + ") during test (" + trace + ")" );
        }

    	@Override
	    public void
    	downloadAdded(
    		Download	download )
    	{
    		if ( test_torrent != null ){

    			try{
	    			if ( Arrays.equals( download.getTorrent().getHash(), test_torrent.getHash())){

	    				return;
	    			}
    			}catch( Throwable e ){

    				Debug.printStackTrace(e);
    			}
    		}

            String downloadAdded = MessageText.getString("SpeedTestWizard.abort.message.download.added"
                    , new String[]{download.getName()});
            abort(downloadAdded);
    	}

    	@Override
	    public void
    	downloadRemoved(
    		Download	download )
    	{
    	}

        public void
        saveLimits()
        {
        		// a bunch of plugins mess with limits (AutoSpeed, Shaper, SpeedScheduler...) - disable their
        		// ability to mess with config during the test

        	PluginConfigImpl.setEnablePluginCoreConfigChange( false );

        	plugin.getDownloadManager().addListener( this, false );

            //preserve the limits for all the downloads and set each to zero.
            Download[] d = plugin.getDownloadManager().getDownloads();
            if(d!=null){
                int len = d.length;
                for(int i=0;i<len;i++){

                    plugin.getDownloadManager().getStats();
                    int downloadLimit = d[i].getDownloadRateLimitBytesPerSecond();
                    int uploadLimit = d[i].getUploadRateLimitBytesPerSecond();

                    setDownloadDetails(d[i],uploadLimit,downloadLimit);

                    d[i].setUploadRateLimitBytesPerSecond(ZERO_DOWNLOAD_SETTING);
                    d[i].setDownloadRateLimitBytesPerSecond( ZERO_DOWNLOAD_SETTING );
                }
            }

            //preserve the global limits

            saveGlobalLimits();

            COConfigurationManager.setParameter( "LAN Speed Enabled", false );

            COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,false);
            COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY,false);

            COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY, max_speed);
            COConfigurationManager.setParameter( TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY, max_speed);
            COConfigurationManager.setParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY, max_speed);

            String[]	params = TransferSpeedValidator.CONFIG_PARAMS;

        	for (int i=0;i<params.length;i++){
        		COConfigurationManager.addParameterListener( params[i], this );
        	}
        }

        public void
        restoreLimits()
        {
        	String[]	params = TransferSpeedValidator.CONFIG_PARAMS;

        	for (int i=0;i<params.length;i++){
        		COConfigurationManager.removeParameterListener( params[i], this );
        	}

           	plugin.getDownloadManager().removeListener( this );

         	restoreGlobalLimits();

        	restoreIndividualLimits();

           	PluginConfigImpl.setEnablePluginCoreConfigChange( true );
        }

        /**
         * Get the global limits from the TransferSpeedValidator class. Call before starting a speed test.
         */
        private void saveGlobalLimits(){
            //int settings.
            maxUploadKbs = COConfigurationManager.getIntParameter( TransferSpeedValidator.UPLOAD_CONFIGKEY );
            maxUploadSeedingKbs = COConfigurationManager.getIntParameter( TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY );
            maxDownloadKbs = COConfigurationManager.getIntParameter( TransferSpeedValidator.DOWNLOAD_CONFIGKEY );
            //boolean setting.
            autoSpeedEnabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY );
            autoSpeedSeedingEnabled = COConfigurationManager.getBooleanParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY );

            LANSpeedEnabled = COConfigurationManager.getBooleanParameter( "LAN Speed Enabled" );

        }//saveGlobalLimits

        /**
         * Call this method after a speed test completes to restore the global limits.
         */
        private void restoreGlobalLimits(){
            COConfigurationManager.setParameter( "LAN Speed Enabled", LANSpeedEnabled );

            COConfigurationManager.setParameter(TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,autoSpeedEnabled);
            COConfigurationManager.setParameter(TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY,autoSpeedSeedingEnabled);

            COConfigurationManager.setParameter(TransferSpeedValidator.UPLOAD_CONFIGKEY,maxUploadKbs);
            COConfigurationManager.setParameter(TransferSpeedValidator.UPLOAD_SEEDING_CONFIGKEY,maxUploadSeedingKbs);
            COConfigurationManager.setParameter(TransferSpeedValidator.DOWNLOAD_CONFIGKEY,maxDownloadKbs);
        }//restoreGlobalLimits

        /**
         * Call this method after the speed test is completed to restore the individual download limits
         * before the test started.
         */
        private void restoreIndividualLimits(){
            Download[] downloads = getAllDownloads();
            if(downloads!=null){
                int nDownloads = downloads.length;

                for(int i=0;i<nDownloads;i++){
                    int uploadLimit = getDownloadDetails(downloads[i], TORRENT_UPLOAD_LIMIT);
                    int downLimit = getDownloadDetails(downloads[i], TORRENT_DOWNLOAD_LIMIT);

                    downloads[i].setDownloadRateLimitBytesPerSecond(downLimit);
                    downloads[i].setUploadRateLimitBytesPerSecond(uploadLimit);
                }
            }
        }
        /**
         * Save the upload/download limits of this Download object before the test started.
         * @param d - Download
         * @param uploadLimit - int
         * @param downloadLimit - int
         */
        private void setDownloadDetails(Download d, int uploadLimit, int downloadLimit){
            if(d==null)
                throw new IllegalArgumentException("Download should not be null.");

            Map props = new HashMap();//Map<String,Integer>

            props.put(TORRENT_UPLOAD_LIMIT, new Integer(uploadLimit) );
            props.put(TORRENT_DOWNLOAD_LIMIT, new Integer(downloadLimit) );

            torrentLimits.put(d,props);
        }

        /**
         * Get the upload or download limit for this Download object before the test started.
         * @param d - Download
         * @param param - String
         * @return - limit as int.
         */
        private int getDownloadDetails(Download d, String param){
            if(d==null || param==null )
                throw new IllegalArgumentException("null inputs.");

            if(!param.equals(TORRENT_UPLOAD_LIMIT) && !param.equals(TORRENT_DOWNLOAD_LIMIT))
                throw new IllegalArgumentException("invalid param. param="+param);

            Map out = (Map) torrentLimits.get(d);
            Integer limit = (Integer) out.get(param);

            return limit.intValue();
        }

        /**
         * Get all the Download keys in this Map.
         * @return - Download[]
         */
        private Download[] getAllDownloads(){
            Download[] a = new Download[0];
            return (Download[]) torrentLimits.keySet().toArray(a);
        }

    }

	protected void
	reportStage(
		String	str )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetworkAdminSpeedTestScheduledTestListener)it.next()).stage( this, str );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	reportComplete()
	{
		resetSpeedLimits();

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetworkAdminSpeedTestScheduledTestListener)it.next()).complete( this );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	public void
	addListener(
		NetworkAdminSpeedTestScheduledTestListener	listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		NetworkAdminSpeedTestScheduledTestListener	listener )
	{
		listeners.remove( listener );
	}

}
