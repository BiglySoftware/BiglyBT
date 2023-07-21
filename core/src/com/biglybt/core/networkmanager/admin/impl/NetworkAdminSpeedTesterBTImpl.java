/**
* Created on Apr 17, 2007
* Created by Alan Snyder
* Copyright (C) Azureus Software, Inc, All Rights Reserved.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*
*/

package com.biglybt.core.networkmanager.admin.impl;

import java.io.File;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTestScheduler;
import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTester;
import com.biglybt.core.networkmanager.admin.NetworkAdminSpeedTesterResult;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.security.SECertificateListener;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;


public class NetworkAdminSpeedTesterBTImpl
	extends NetworkAdminSpeedTesterImpl
	implements NetworkAdminSpeedTester
{
    public static final String DOWNLOAD_AVE = "download-ave";
    public static final String UPLOAD_AVE = "upload-ave";
    public static final String DOWNLOAD_STD_DEV = "download-std-dev";
    public static final String UPLOAD_STD_DEV = "upload-std-dev";

    static int testMode = TEST_TYPE_UPLOAD_ONLY;

    private static TorrentAttribute speedTestAttrib;

    static NetworkAdminSpeedTesterResult	lastResult;

    protected static void
    initialise()
    {
      PluginInterface plugin = PluginInitializer.getDefaultInterface();

      speedTestAttrib = plugin.getTorrentManager().getPluginAttribute(NetworkAdminSpeedTesterBTImpl.class.getName()+".test.attrib");
    }

    protected static void
    startUp()
    {
    	PluginInterface plugin = PluginInitializer.getDefaultInterface();

    	com.biglybt.pif.download.DownloadManager dm = plugin.getDownloadManager();
    	Download[] downloads = dm.getDownloads();

    	if(downloads!=null){
    		int num = downloads.length;
    		for(int i=0; i<num; i++){
    			Download	download = downloads[i];
    			if( download.getBooleanAttribute(speedTestAttrib) ){
    				try{
    					if (download.getState() != Download.ST_STOPPED ){
    						try{
    							download.stop();
    						}catch( Throwable e ){
    							Debug.out(e);
    						}
    					}
    					download.remove(true,true);
     				}catch(Throwable e ){
    					Debug.out("Had "+e.getMessage()+" while trying to remove "+downloads[i].getName());
    				}
    			}
    		}
    	}
    }

    protected static NetworkAdminSpeedTesterResult
    getLastResult()
    {
    	return( lastResult );
    }


    private final PluginInterface plugin;


    private boolean	test_started;
    boolean	test_completed;

    boolean	use_crypto;

    volatile boolean	aborted;
    String				deferred_abort;

    /**
     *
     * @param pi - PluginInterface is used to get Manager classes.
     */
    public NetworkAdminSpeedTesterBTImpl(PluginInterface pi){
        plugin = pi;
    }

    @Override
    public int
    getTestType()
    {
    	return( NetworkAdminSpeedTestScheduler.TEST_TYPE_BT );
    }

    @Override
    public void setMode(int mode) {
        testMode = mode;
    }

    @Override
    public int
    getMode()
    {
    	return( testMode );
    }

    @Override
    public void
    setUseCrypto(
    	boolean	_use_crypto )
    {
    	use_crypto = _use_crypto;
    }

    @Override
    public boolean
    getUseCrypto()
    {
    	return( use_crypto );
    }

    /**
     * The downloads have been stopped just need to do the testing.
     * @param tot - Torrent received from testing service.
     */
    public synchronized void
    start(
    	TOTorrent	tot )
    {
    	if ( test_started ){

    		Debug.out( "Test already started!" );

    		return;
    	}

    	test_started = true;

        //OK lets start the test.
        try{
            TorrentUtils.setFlag( tot, TorrentUtils.TORRENT_FLAG_LOW_NOISE, true );

            Torrent torrent = new TorrentImpl(tot);
            String fileName = torrent.getName();

            sendStageUpdateToListeners(MessageText.getString("SpeedTestWizard.stage.message.preparing"));

            //create a blank file of specified size. (using the temporary name.)
            File saveLocation = AETemporaryFileHandler.createTempFile();
            File baseDir = saveLocation.getParentFile();
            File blankFile = FileUtil.newFile(baseDir, fileName);
            File blankTorrentFile = FileUtil.newFile(baseDir, "speedTestTorrent.torrent");
            torrent.writeToFile(blankTorrentFile);

            URL	announce_url = torrent.getAnnounceURL();

            if ( announce_url.getProtocol().equalsIgnoreCase( "https" )){

            	SESecurityManager.setCertificateHandler(
            			announce_url,
            			new SECertificateListener()
            			{
            				@Override
				            public boolean
            				trustCertificate(
            					String			resource,
            					X509Certificate	cert )
            				{
            					return( true );
            				}
            			});
            }

            Download speed_download = plugin.getDownloadManager().addDownloadStopped( torrent, blankTorrentFile ,blankFile);

            speed_download.setBooleanAttribute(speedTestAttrib,true);

            DownloadManager core_download = PluginCoreUtils.unwrap( speed_download );

            core_download.setPieceCheckingEnabled( false );

            	// make sure we've got a bunch of upload slots

            core_download.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS, 32 );
            core_download.getDownloadState().setIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING, 32 );

            if ( use_crypto ){

            	core_download.setCryptoLevel( NetworkManager.CRYPTO_OVERRIDE_REQUIRED );
            }

            core_download.addPeerListener(
            		new DownloadManagerPeerListener()
            		{
            			@Override
			            public void
            			peerManagerWillBeAdded(
            				PEPeerManager	peer_manager )
            			{
              				DiskManager	disk_manager = peer_manager.getDiskManager();

                			DiskManagerPiece[]	pieces = disk_manager.getPieces();

                            int startPiece = setStartPieceBasedOnMode(testMode,pieces.length);

                            for ( int i=startPiece; i<pieces.length; i++ ){
                                pieces[i].setDone( true );
                			}
            			}

            			@Override
			            public void
            			peerManagerAdded( PEPeerManager	peer_manager )
            			{
            			}

            			@Override
			            public void
            			peerManagerRemoved(PEPeerManager	manager )
            			{
            			}

            			@Override
			            public void
            			peerAdded(PEPeer 	peer )
            			{
            			}

            			@Override
			            public void
            			peerRemoved(PEPeer	peer )
            			{
            			}
                	});

            speed_download.moveTo( 1 );

            speed_download.setFlag( Download.FLAG_DISABLE_AUTO_FILE_MOVE, true );
            speed_download.setFlag( Download.FLAG_DISABLE_STOP_AFTER_ALLOC, true );

            core_download.initialize();

            core_download.setForceStart( true );

            TorrentSpeedTestMonitorThread monitor = new TorrentSpeedTestMonitorThread( speed_download );

            monitor.start();

            //The test has now started!!

        }catch( Throwable e){

        	test_completed = true;

            abort( "Could not start test", e );
        }
    }


    public void
    complete(
    	NetworkAdminSpeedTesterResult		result )
    {
    	sendResultToListeners( result );
    }

    @Override
    protected void
    abort(
    	String		reason,
    	Throwable	cause )
    {
    	String	msg;

    	if ( cause instanceof RuntimeException ){

    		msg = Debug.getNestedExceptionMessageAndStack( cause );

    	}else{

    		msg = Debug.getNestedExceptionMessage( cause );
    	}

    	abort( reason + ": " + msg );
    }

	@Override
	public void
	abort(
		String reason )
	{
		reason = "Test aborted: " + reason;

		synchronized( this ){

			if ( aborted ){

				return;
			}

			aborted = true;

				// we need to defer the reporting of a failure until the test is complete
				// as this prevents us from starting another test while the current one is
				// terminating

			if ( test_started && !test_completed ){

				deferred_abort = reason;

				return;
			}
		}

        sendResultToListeners( new BitTorrentResult( reason ));
    }


    /**
	 * Get the result for
	 * @return Result object of speed test.
	 */
	public NetworkAdminSpeedTesterResult getResult(){
        return lastResult;
    }


    // ------------------ private methods ---------------

    /**
     * Depending on the mode we want to upload all the set all, none or only
     * half the pieces to done.
     * @param mode - int that maps to NetworkAdminSpeedTestScheduler.TEST_TYPE...
     * @param totalPieces - total pieces in this test torrent.
     * @return - int - the starting piece number to setDone to true.
     */
    static int setStartPieceBasedOnMode(int mode, int totalPieces){

        //if(mode==TEST_TYPE_UPLOAD_AND_DOWNLOAD){
        //    //upload half the pieces
        //    return totalPieces/2;
        //}else
        if(mode==TEST_TYPE_UPLOAD_ONLY){
            //upload all the pieces
            return 0;
        }else if(mode==TEST_TYPE_DOWNLOAD_ONLY){
            //download all the pieces
            return totalPieces;
        }
        else
            throw new IllegalStateException("Did not recognize the NetworkAdmin Speed Test type. mode="+mode);
    }


    /**   -------------------- helper class to monitor test. ------------------- **/
    private class TorrentSpeedTestMonitorThread
        extends Thread
    {
        final List historyDownloadSpeed = new LinkedList();  //<Long>
        final List historyUploadSpeed = new LinkedList();    //<Long>
        final List timestamps = new LinkedList();            //<Long>

        final Download testDownload;

        public static final long MAX_TEST_TIME = 2*60*1000; //Limit test to 2 minutes.
        public static final long MAX_PEAK_TIME = 30 * 1000; //Limit to 30 seconds at peak.
        long startTime;
        long peakTime;
        long peakRate;

        public static final String AVE = "ave";
        public static final String STD_DEV = "stddev";

        public TorrentSpeedTestMonitorThread( Download d )
        {
            testDownload = d;
        }

        @Override
        public void run()
        {
        	try{
	            Set	connected_peers 	= new HashSet();
	            Set	not_choked_peers 	= new HashSet();
	            Set	not_choking_peers 	= new HashSet();

	            try{

	                startTime = SystemTime.getCurrentTime();
	                peakTime = startTime;

	                boolean testDone=false;
	                long lastTotalTransferredBytes=0;

                      sendStageUpdateToListeners(MessageText.getString("SpeedTestWizard.stage.message.starting"));
                      while( !( testDone || aborted )){

	                	int state = testDownload.getState();

	                	if ( state == Download.ST_ERROR ){

                            String enteredErrorState = MessageText.getString("SpeedTestWizard.abort.message.entered.error"
                                    , new String[] {testDownload.getErrorStateDetails()} );
                            abort( enteredErrorState );

	                		break;
	                	}

	                	if (  state == Download.ST_STOPPED ){

                            abort( MessageText.getString("SpeedTestWizard.abort.message.entered.queued") );

	                		break;
	                	}

	                		// can flick out of force-mode when transitioning from downloading
	                		// to seeding - easiest fix is:

	                	if ( !testDownload.isForceStart()){

	                		testDownload.setForceStart( true );
	                	}

	                	PeerManager pm = testDownload.getPeerManager();

	                	if ( pm != null ){

	                		Peer[] peers = pm.getPeers();

	                		for ( int i=0;i<peers.length;i++){

	                			Peer peer = peers[i];

	                				// use the IP as the key so we don't count reconnects multiple times

	                			String	key = peer.getIp();

	                			connected_peers.add( key );

	                			if ( !peer.isChoked()){

	                				not_choked_peers.add( key );
	                			}

	                			if ( !peer.isChoking()){

	                				not_choking_peers.add( key );
	                			}
	                		}
	                	}

	                    long currTime = SystemTime.getCurrentTime();
	                    DownloadStats stats = testDownload.getStats();
	                    historyDownloadSpeed.add( autoboxLong(stats.getDownloaded( true )) );
	                    historyUploadSpeed.add( autoboxLong(stats.getUploaded( true )) );
	                    timestamps.add( autoboxLong(currTime) );

	                    updateTestProgress(currTime,stats);

	                    lastTotalTransferredBytes = checkForNewPeakValue( stats, lastTotalTransferredBytes, currTime );

	                    testDone = checkForTestDone();
	                    if(testDone)
	                        break;

	                    try{ Thread.sleep(1000); }
	                    catch(InterruptedException ie){
	                        //someone interrupted this thread for a reason. "test is now over"
                            abort( MessageText.getString("SpeedTestWizard.abort.message.interrupted") );

	                        break;
	                    }

	                }

	                //It is time to stop the test.
	                try{
	                	if ( testDownload.getState() != Download.ST_STOPPED){
	                		try{
	                			testDownload.stop();
	                		}catch( Throwable e ){
	                			Debug.printStackTrace(e);
	                		}
	                	}
	                	testDownload.remove(true,true);

	                }catch(DownloadException de){

	                    abort( "TorrentSpeedTestMonitorThread could not stop the torrent "+testDownload.getName(), de);

	                }catch(DownloadRemovalVetoException drve){

	                	abort( "TorrentSpeedTestMonitorTheard could not remove the torrent "+testDownload.getName(), drve);
	                }

	            }catch(Exception e){

                    abort( MessageText.getString("SpeedTestWizard.abort.message.execution.failed"),e );
                }

	            if ( !aborted ){

	            		// check the stats for peers we connected to during the test
                    String connectStats = MessageText.getString("SpeedTestWizard.stage.message.connect.stats",
                            new String[]{""+connected_peers.size()
                                    ,""+not_choked_peers.size()
                                    ,""+not_choking_peers.size()
                            });
                    sendStageUpdateToListeners(connectStats);

                    if ( connected_peers.size() == 0 ){

                        abort( MessageText.getString("SpeedTestWizard.abort.message.failed.peers") );

	                }else if ( not_choking_peers.size() == 0 && testMode!=TEST_TYPE_DOWNLOAD_ONLY ){

                        abort( MessageText.getString("SpeedTestWizard.abort.message.insufficient.slots") );

		            }else if ( not_choked_peers.size() == 0 && testMode!=TEST_TYPE_UPLOAD_ONLY){

                        abort( MessageText.getString("SpeedTestWizard.abort.message.not.unchoked") );
		            }
	            }

	            if ( !aborted ){

		            //calculate the measured download rate.
		            NetworkAdminSpeedTesterResult r = calculateDownloadRate();

		            lastResult = r;

		            	// TODO: persist it

		            //Log the result.
		            AEDiagnosticsLogger diagLogger = AEDiagnostics.getLogger("v3.STres");
		            diagLogger.log(r.toString());

		            complete(r);
	            }
        	}finally{

        		synchronized( NetworkAdminSpeedTesterBTImpl.this ){

        			test_completed	= true;

        			if ( deferred_abort != null ){

        		        sendResultToListeners( new BitTorrentResult( deferred_abort ));
        			}
        		}
        	}
        }//run.

        /**
         * Calculate the test progression as a value between 0-100.
         * @param currTime - current time as long.
         * @param stats - Download stats
         */
        public void updateTestProgress(long currTime, DownloadStats stats){

            //do two calculations. First based on the total time allowed for the test
            long totalDownloadTimeUsed = currTime-startTime;
            float percentTotal = ((float)totalDownloadTimeUsed/(float)MAX_TEST_TIME);

            //second for the time since the peak value has been reached.
            long totalTestTimeUsed = currTime-peakTime;
            float percentDownload = ((float)totalTestTimeUsed/(float)MAX_PEAK_TIME);

            //the larger of the two wins.
            float reportedProgress = percentTotal;
            if( percentDownload>reportedProgress )
                reportedProgress=percentDownload;

            int progressBarVal = Math.round( reportedProgress*100.0f );
            StringBuilder msg = new StringBuilder("progress: ");
            msg.append(  progressBarVal );
            //include the upload and download values.
            msg.append(" : download ave ");
            msg.append( stats.getDownloadAverage( true ) );
            msg.append(" : upload ave ");
            msg.append( stats.getUploadAverage( true ) );
            msg.append(" : ");
            int totalTimeLeft = (int)((MAX_TEST_TIME-totalDownloadTimeUsed)/1000);
            msg.append(totalTimeLeft);
            msg.append(" : ");
            int testTimeLeft = (int)((MAX_PEAK_TIME-totalTestTimeUsed)/1000);
            msg.append(testTimeLeft);

            sendStageUpdateToListeners( msg.toString() );

        }//updateTestProgress


        /**
         * Calculate the avererage and standard deviation for a history.
         * @param history - List of Long values but that contains the sum downloaded at that time.
         * @return Map<String,Double> with values "ave" and "stddev" set
         */
        //Map<String,Double> calculate(List<Long> history)
        private Map calculate(List history){

            //convert the list of long values that sum the value into a list of deltas.
            List deltas = convertSumToDeltas(history);

            //sort
            Collections.sort(deltas);

            //remove the top and bottom 10% of the sample. This removes outliers from the mean.
            final int nSamples = deltas.size();
            final int nRemove = nSamples/10;

             for(int i=0; i<nRemove; i++){
                deltas.remove(0);
	            deltas.remove(deltas.size()-1);
            }

            //sum values
            long sumBytes=0;
            int j=0;
            while(j<deltas.size()){
                sumBytes += autoboxLong( deltas.get(j) );
                j++;
            }
            //calculate average.
            double aveRate = ((double)sumBytes)/deltas.size();
            //Debug.out("ave rate:"+aveRate);

            //calculate standard deviation.
            double variance = 0.0;
            double s;
            for(j=0;j<deltas.size();j++){
                //Debug.out( j+","+deltas.get(j) );

                s = ( autoboxLong(deltas.get(j)) - aveRate );
                variance += s*s;
            }
            double stddev = Math.sqrt( variance/(j-1) );

            //Map<String,Double> retVal = new HashMap<>();
            Map retVal = new HashMap();
            retVal.put(AVE, autoboxDouble(aveRate));
            retVal.put(STD_DEV,autoboxDouble(stddev));
            return retVal;
        }//calculate


        /**
         * Convert a list of sums into a list of download rates per second.
         * @param sumHistory - List<Long> with download sum for each second.
         * @return - List<Long> with the download rate for each second.
         */
        private List convertSumToDeltas(List sumHistory){
            //find the first element to include in the stat.
            int numStats = sumHistory.size();
            int i = findIndexPeak(numStats);

            List deltas = new ArrayList(numStats);
            if (i == 0) {return deltas;}
            long prevSumDownload = autoboxLong(sumHistory.get(i-1));
            long currSumDownload;
            while(i<numStats){

                currSumDownload = autoboxLong( sumHistory.get(i) );
                Long currDelta = autoboxLong( currSumDownload - prevSumDownload );

                deltas.add(currDelta);
                i++;
                prevSumDownload = currSumDownload;
            }//while

            return deltas;
        }//convertSumToDeltas

        private int findIndexPeak(int numStats) {
            long thisTime;
            int i;
            for(i=0;i<numStats;i++ ){
                thisTime = autoboxLong( timestamps.get(i) );
                if(thisTime>peakTime){
                    break;
                }
            }//for
            return i;
        }

        /**
         * Based on the previous data cancluate an average and a standard deviation.
         * Return this data in a Map object.
         * @return Map<String,Float> as a contain for stats. Map keys are "ave" and "dev".
         */
        NetworkAdminSpeedTesterResult calculateDownloadRate()
        {
            //calculate the BT download rate.
            //Map<String,Double> resDown = calculate(historyDownloadSpeed);
            Map resDown = calculate(historyDownloadSpeed);

            //calculate the BT upload rate.
            //Map<String,Double> resUp = calculate(historyUploadSpeed);
            Map resUp = calculate(historyUploadSpeed);

            return new BitTorrentResult(resUp,resDown);
        }//calculateDownloadRate


        /**
         * In this version the test is limited to MAX_TEST_TIME since the start of the test
         * of MAX_PEAK_TIME (i.e. time since the peak download rate has been reached). Which
         * ever condition is first will finish the download.
         * @return true if the test done condition has been reached.
         */
        boolean checkForTestDone(){

            long currTime = SystemTime.getCurrentTime();
            //have we reached the max time for this test?
            if( (currTime-startTime)>MAX_TEST_TIME ){
                return true;
            }

            //have we been near the peak download value for max time?
            return (currTime - peakTime) > MAX_PEAK_TIME;
        }//checkForTestDone


        /**
         * We set a new "peak" value if it has exceeded the previous peak value by 10%.
         * @param stat -
         * @param lastTotalDownload -
         * @param currTime -
         * @return total downloaded so far.
         */
        long checkForNewPeakValue(DownloadStats stat, long lastTotalDownload, long currTime)
        {
            //upload only used the "uploaded" data. The "download only" and "both" uses download.
            long totTransferred;
            if(testMode==TEST_TYPE_UPLOAD_ONLY){
                totTransferred = stat.getUploaded( true );
            }else{
                totTransferred = stat.getDownloaded(true);
            }
            long currTransferRate = totTransferred-lastTotalDownload;

            //if the current rate is 10% greater then the previous max, reset the max, and test timer.
            if( currTransferRate > peakRate ){
                peakRate = (long) (currTransferRate*1.1);
                peakTime = currTime;
            }

            return totTransferred;
        }//checkForNewPeakValue

    }//class TorrentSpeedTestMonitorThread

    class BitTorrentResult implements NetworkAdminSpeedTesterResult{

        final long time;
        int downspeed;
        int upspeed;
        boolean hadError = false;
        String lastError = "";

        /**
         * Build a Result for a successful test.
         * @param uploadRes - Map<String,Double> of upload results.
         * @param downloadRes - Map<String,Double> of download results.
         */
        public BitTorrentResult(Map uploadRes, Map downloadRes){
            time = SystemTime.getCurrentTime();
            Double dAve = (Double)downloadRes.get(TorrentSpeedTestMonitorThread.AVE);
            Double uAve = (Double)uploadRes.get(TorrentSpeedTestMonitorThread.AVE);
            downspeed = dAve.intValue();
            upspeed = uAve.intValue();
        }

        /**
         * Build a Result if the test failed with an error.
         * @param errorMsg - why the test failed.
         */
        public BitTorrentResult(String errorMsg){
            time = SystemTime.getCurrentTime();
            hadError=true;
            lastError = errorMsg;
        }

        @Override
        public NetworkAdminSpeedTester getTest() {
        	return( NetworkAdminSpeedTesterBTImpl.this );
        }

        @Override
        public long getTestTime() {
            return time;
        }

        @Override
        public int getDownloadSpeed() {
            return downspeed;
        }

        @Override
        public int getUploadSpeed() {
            return upspeed;
        }

        @Override
        public boolean hadError() {
            return hadError;
        }

        @Override
        public String getLastError() {
            return lastError;
        }

        public String getResultString(){
            StringBuilder sb = new StringBuilder();

            //Time
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmss z");
            String d = format.format( new Date(time) );
            sb.append(d).append(" ");

            sb.append("type: BT test ");

            //Get test info.
            sb.append("mode: ").append( getMode() );

            //Get crypto
            sb.append(" encrypted: ");
            if( use_crypto ){
                sb.append("y");
            }else{
                sb.append("n");
            }

            if(hadError){
                //Error
                sb.append(" Last Error: ").append(lastError);
            }else{
                //Result
                sb.append(" download speed: ").append(downspeed).append(" bits/sec");
                sb.append(" upload speed: ").append(upspeed).append(" bits/sec");
            }

            return sb.toString();
        }//getString


        public String toString(){
            StringBuilder sb = new StringBuilder("[com.biglybt.core.networkmanager.admin.impl.NetworkAdminSpeedTesterBTImpl");

            sb.append(" ").append( getResultString() ).append(" ");
            sb.append("]");

            return sb.toString();
        }
    }//class BitTorrentResult


    static long autoboxLong(Object o){
        return autoboxLong( (Long) o );
    }

    private static long autoboxLong(Long l){
        return l.longValue();
    }

    static Long autoboxLong(long l){
        return new Long(l);
    }

    static Double autoboxDouble(double d){
        return new Double(d);
    }
}//class
