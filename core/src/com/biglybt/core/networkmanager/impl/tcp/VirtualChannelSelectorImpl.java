/*
 * Created on Jul 28, 2004
 * Created by Alon Rohter
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
package com.biglybt.core.networkmanager.impl.tcp;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.*;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.util.*;



/**
 * Provides a simplified and safe (selectable-channel) socket single-op selector.
 */
public class VirtualChannelSelectorImpl {

	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final boolean MAYBE_BROKEN_SELECT;

	static{

			// freebsd 7.x and diablo 1.6 no works as selector returns none ready even though
			// there's a bunch readable

			// Seems to not just be diablo java, but general 7.1 problem

		String jvm_name = System.getProperty( "java.vm.name", "" );

		boolean is_diablo = jvm_name.startsWith( "Diablo" );

		boolean is_freebsd_7_or_higher = false;

			// hack for 10.6 - will switch to not doing System.setProperty( "java.nio.preferSelect", "true" ); later

		try{
				// unfortunately the package maintainer has set os.name to Linux for FreeBSD...

			if ( Constants.isFreeBSD || Constants.isLinux ){

				String os_type = System.getenv( "OSTYPE" );

				if ( os_type != null && os_type.equals( "FreeBSD" )){

					String os_version = Constants.OSVersion;

					String	digits = "";

					for ( int i=0;i<os_version.length();i++){

						char c = os_version.charAt(i);

						if ( Character.isDigit(c)){

							digits += c;

						}else{

							break;
						}
					}

					if ( digits.length() > 0 ){

						is_freebsd_7_or_higher = Integer.parseInt(digits) >= 7;
					}
				}
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

		MAYBE_BROKEN_SELECT = is_freebsd_7_or_higher || is_diablo || Constants.isOSX;

		if ( MAYBE_BROKEN_SELECT ){

			System.out.println( "Enabling broken select detection: diablo=" + is_diablo + ", freebsd 7+=" + is_freebsd_7_or_higher + ", osx 10.6+=" + Constants.isOSX );
		}
	}

	private static final int SELECTOR_TIMEOUT	= 15*1000;

	static final AESemaphore get_selector_allowed = new AESemaphore( "getSelectorAllowed", 1 );

	private static class
	SelectorTimeoutException
		extends IOException
	{
		SelectorTimeoutException()
		{
			super( "Selector allocation timeout" );
		}
	}

	private static Selector
	getSelector()

		throws IOException
	{
			// we only want to allow one thread actively attempting to get a selector otherwise they'll just stack up

		if ( !get_selector_allowed.reserve( SELECTOR_TIMEOUT )){

			Debug.out( "Selector timeout (existing incomplete)" );

			throw( new SelectorTimeoutException());
		}

			// some AV products/VPNs in a bad state can block selector opening - prevent this from blocking everything

		final Object[]	result = { null };

		final AESemaphore sem = new AESemaphore( "getSelector" );

		synchronized( VirtualChannelSelectorImpl.class ){

			try{
				final TimerEvent event =
					SimpleTimer.addEvent(
							"getSelector",
							SystemTime.getOffsetTime( SELECTOR_TIMEOUT ),
							new TimerEventPerformer() {

								@Override
								public void
								perform(
									TimerEvent event )
								{
									synchronized( VirtualChannelSelectorImpl.class ){

										if ( result[0] == null ){

											Debug.out( "Selector timeout" );

											result[0] = new SelectorTimeoutException();

											sem.release();
										}
									}
								}
							});

				new AEThread2( "getSelector" )
				{
					@Override
					public void
					run()
					{
						try{
							Selector sel = Selector.open();

							synchronized( VirtualChannelSelectorImpl.class ){

								if ( result[0] == null ){

									result[0] = sel;

									return;
								}
							}

							sel.close();

						}catch( Throwable e ){

							synchronized( VirtualChannelSelectorImpl.class ){

								if ( result[0] == null ){

									if ( e instanceof IOException ){

										result[0]	= e;

									}else{

										result[0] = new IOException( Debug.getNestedExceptionMessage( e ));
									}
								}
							}
						}finally{

							get_selector_allowed.release();

							sem.release();

							event.cancel();
						}
					}
				}.start();

			}catch( Throwable e ){

				get_selector_allowed.release();

				throw( new IOException( Debug.getNestedExceptionMessage( e )));
			}
		}

		sem.reserve();

		//System.out.println( "getSelector-> " + result[0]);

		if ( result[0] instanceof IOException ){

			throw((IOException)result[0]);
		}

		return((Selector)result[0]);
	}

	private boolean select_is_broken;
	private int		select_looks_broken_count;
	private boolean	logged_broken_select;


	/*
	static boolean	rm_trace 	= false;
	static boolean	rm_test_fix = false;

	static{

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{ "user.rm.trace", "user.rm.testfix" },
				new ParameterListener()
				{
					public void
					parameterChanged(
						String parameterName)
					{
						rm_trace 	= COConfigurationManager.getBooleanParameter( "user.rm.trace", false );
						rm_test_fix = COConfigurationManager.getBooleanParameter( "user.rm.testfix", false );
					}
				});
	}

	private long rm_flag_last_log;
	private Map	rm_listener_map = new HashMap();
	*/

    protected Selector selector;
    private final SelectorGuard selector_guard;

    private int	consec_select_fails;
    private long consec_select_fails_start;

    private final Map<AbstractSelectableChannel,Object> 	register_cancel_list 		= new LinkedHashMap<>();
    
    private final AEMonitor 			register_cancel_list_mon	= new AEMonitor( "VirtualChannelSelector:RCL");

    private final HashMap<AbstractSelectableChannel, Boolean> paused_states = new HashMap<>();

    private final int 		INTEREST_OP;
    private final boolean	pause_after_select;

    protected final VirtualChannelSelector parent;


    //private int[] select_counts = new int[ 50 ];
    //private int round = 0;

    private volatile boolean	destroyed;

    private boolean	randomise_keys;

    private int		next_select_loop_pos = 0;

    private static final int WRITE_SELECTOR_DEBUG_CHECK_PERIOD	= 10000;
    private static final int WRITE_SELECTOR_DEBUG_MAX_TIME		= 20000;

    private long last_write_select_debug = -1;
    private long last_select_debug;

    private long last_reopen_attempt = SystemTime.getMonotonousTime();

    public VirtualChannelSelectorImpl( VirtualChannelSelector _parent, int _interest_op, boolean _pause_after_select, boolean _randomise_keys ) {
      this.parent = _parent;
      INTEREST_OP = _interest_op;

      pause_after_select	= _pause_after_select;
      randomise_keys		= _randomise_keys;

      String type;
      switch( INTEREST_OP ) {
        case VirtualChannelSelector.OP_CONNECT:
          type = "OP_CONNECT";  break;
        case VirtualChannelSelector.OP_READ:
          type = "OP_READ";  break;
        default:
          type = "OP_WRITE";  break;
      }


      selector_guard = new SelectorGuard( type);

      selector = openNewSelector();
    }



    protected Selector openNewSelector() {
      Selector sel = null;

      final int MAX_TRIES = 10;

      try {
        sel = getSelector();

        AEDiagnostics.logWithStack( "seltrace", "Selector created for '" + parent.getName() + "'," + selector_guard.getType());
      }
      catch (Throwable t) {
        Debug.out( "ERROR: caught exception on Selector.open() for '" + parent.getName() + "'", t );

        try {  Thread.sleep( 3000 );  }catch( Throwable x ) {x.printStackTrace();}

        int fail_count = (t instanceof SelectorTimeoutException?1000:1);

        while( fail_count < MAX_TRIES ) {

          try {
            sel = getSelector();

            AEDiagnostics.logWithStack( "seltrace", "Selector created for '" + parent.getName() + "'," + selector_guard.getType());

            break;

          }catch( Throwable f ) {

            Debug.out( f );

            if ( f instanceof SelectorTimeoutException ){

            	fail_count = 1000;

            }else{

            	fail_count++;
            }

            if ( fail_count < MAX_TRIES ){

            	try {  Thread.sleep( 3000 );  }catch( Throwable x ) {x.printStackTrace();}

            }else{

            	break;
            }
          }
        }

        if( fail_count < MAX_TRIES ) { //success !
          Debug.out( "NOTICE: socket Selector for '" + parent.getName() + "' successfully opened after " +fail_count+ " failures." );
        }
        else {  //failure
        	Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
						"ERROR: socket Selector.open() for '" + parent.getName() + "' failed " + ( fail_count==1000?"due to timeout": (MAX_TRIES + " times in a row" )) + ", aborting."
								+ "\nThis App / Java is likely being firewalled!"));
        }
      }

      return sel;
    }


    public void
    setRandomiseKeys(
    	boolean		r )
    {
    	randomise_keys = r;
    }


    public boolean
    isPaused(
    	AbstractSelectableChannel	channel )
    {
        SelectionKey key = channel.keyFor( selector );

        if( key != null && key.isValid() ) {
        	
        	return((  key.interestOps() & INTEREST_OP ) == 0 );
        }else{
            try{  
            	register_cancel_list_mon.enter();

            	Boolean b = paused_states.get( channel );

            	return( b != null && b );
            	
          }finally{ 
        	  register_cancel_list_mon.exit();  
          }
        }
    }
    
    public void pauseSelects( AbstractSelectableChannel channel ) {

      //System.out.println( "pauseSelects: " + channel + " - " + Debug.getCompressedStackTrace() );

      if( channel == null ) {
        return;
      }

      SelectionKey key = channel.keyFor( selector );

      if( key != null && key.isValid() ) {
        key.interestOps( key.interestOps() & ~INTEREST_OP );
      }
      else {  //channel not (yet?) registered
        if( channel.isOpen() ) {  //only bother if channel has not already been closed
          try{  register_cancel_list_mon.enter();

            paused_states.put( channel, Boolean.TRUE);  //ensure the op is paused upon reg select-time reg

          }
          finally{  register_cancel_list_mon.exit();  }
        }
      }
    }
    	

    public void resumeSelects( AbstractSelectableChannel channel ) {
      //System.out.println( "resumeSelects: " + channel + " - " + Debug.getCompressedStackTrace() );
      if( channel == null ) {
        Debug.printStackTrace( new Exception( "resumeSelects():: channel == null" ) );
        return;
      }

      SelectionKey key = channel.keyFor( selector );

      if( key != null && key.isValid() ) {
    	  	// if we're resuming a non-interested key then reset the metrics

    	if (( key.interestOps() & INTEREST_OP ) == 0 ){
     	   RegistrationData data = (RegistrationData)key.attachment();

     	   data.last_select_success_mono_time 	= SystemTime.getMonotonousTime();
     	   data.non_progress_count			= 0;
    	}
        key.interestOps( key.interestOps() | INTEREST_OP );
      }
      else {  //channel not (yet?) registered
        try{  register_cancel_list_mon.enter();
          paused_states.remove( channel );  //check if the channel's op has been already paused before select-time reg
        }
        finally{  register_cancel_list_mon.exit();  }
      }

      //try{
      //  selector.wakeup();
      //}
      //catch( Throwable t ) {  Debug.out( "selector.wakeup():: caught exception: ", t );   }
    }

    public boolean
    isRegistered( AbstractSelectableChannel channel ){
    
      SelectionKey key = channel.keyFor( selector );

         if( key != null ){
        	 return( true );
         }else{
    	  	try{
        		register_cancel_list_mon.enter();

        			// ensure that there's only one operation outstanding for a given channel
        			// at any one time (the latest operation requested )

        		return( register_cancel_list.containsKey( channel ));

        	}finally{

        		register_cancel_list_mon.exit();
        	} 
         }
    }

    public void
	cancel(
		AbstractSelectableChannel channel )
    {
      //System.out.println( "cancel: " + channel + " - " + Debug.getCompressedStackTrace() );

    	if ( destroyed ){

    		// don't worry too much about cancels
    	}

    	if ( channel == null ){

    		Debug.out( "Attempt to cancel selects for null channel" );

    		return;
    	}

    	try{
    		register_cancel_list_mon.enter();

    			// ensure that there's only one operation outstanding for a given channel
    			// at any one time (the latest operation requested )

    		register_cancel_list.remove( channel );

			pauseSelects((AbstractSelectableChannel)channel );

  			register_cancel_list.put( channel, channel );

    	}finally{

    		register_cancel_list_mon.exit();
    	}
    }



    public void
	register(
		AbstractSelectableChannel 								channel,
		VirtualChannelSelector.VirtualAbstractSelectorListener 	listener,
		Object 													attachment )
    {
    	if ( destroyed ){

   			Debug.out( "register called after selector destroyed" );
    	}

    	if ( channel == null ){

    		Debug.out( "Attempt to register selects for null channel" );

    		return;
    	}

    	try{
    		register_cancel_list_mon.enter();

    			// ensure that there's only one operation outstanding for a given channel
    			// at any one time (the latest operation requested )

    		register_cancel_list.remove( channel );

			paused_states.remove( channel );

  			register_cancel_list.put( channel, new RegistrationData( channel, listener, attachment ));

    	}finally{

    		register_cancel_list_mon.exit();
    	}
    }



    public int 
    select( 
    	final long timeout ) 
    {  
      if ( selector == null ){
    	  
        long mono_now = SystemTime.getMonotonousTime();

     	if (( mono_now - last_reopen_attempt > 60*1000 ) && !destroyed){
    		last_reopen_attempt = mono_now;
    		selector = openNewSelector();
    	}
    	if ( selector == null ){
	        Debug.out( "VirtualChannelSelector.select() op called with null selector" );
	        try {  Thread.sleep( 3000 );  }catch( Throwable x ) {x.printStackTrace();}
	        return 0;
    	}
      }

      if( !selector.isOpen()) {
          Debug.out( "VirtualChannelSelector.select() op called with closed selector" );
          try {  Thread.sleep( 3000 );  }catch( Throwable x ) {x.printStackTrace();}
          return 0;
      }

      	// store these when they occur so they can be raised *outside* of the monitor to avoid
      	// potential deadlocks

      RegistrationData	select_fail_data	= null;
      Throwable 		select_fail_excep	= null;

      //process cancellations
      try {
      	register_cancel_list_mon.enter();

      		// don't use an iterator here as it is possible that error notifications to listeners
      		// can result in the addition of a cancel request.
      		// Note that this can only happen for registrations, and this *should* only result in
      		// possibly a cancel being added (i.e. not a further registration), hence this can't
      		// loop. Also note the approach of removing the entry before processing. This is so
      		// that the logic used when adding a cancel (the removal of any matching entries) does
      		// not cause the entry we're processing to be removed

        while( !register_cancel_list.isEmpty()){

          Iterator<Object> it = register_cancel_list.values().iterator();
        	          
          Object	obj = it.next();
          
          it.remove();

          if ( obj instanceof AbstractSelectableChannel ){

         		// process cancellation

        	  AbstractSelectableChannel	canceled_channel = (AbstractSelectableChannel)obj;

            try{
              SelectionKey key = canceled_channel.keyFor( selector );

              if( key != null ){

                key.cancel();  //cancel the key, since already registered
              }

            }catch( Throwable e ){

              Debug.printStackTrace(e);
            }
          }else{
            //process new registrations

            RegistrationData data = (RegistrationData)obj;

            try {

            	if( data == null ) {
            		throw( new Exception( "data == null" ));
            	}

            	else if( data.channel == null ) {
            		throw( new Exception( "data.channel == null" ));
            	}

            	if( data.channel.isOpen() ){

            		// see if already registered
            		SelectionKey key = data.channel.keyFor( selector );

            		if ( key != null && key.isValid() ) {  //already registered
            			key.attach( data );
            			key.interestOps( key.interestOps() | INTEREST_OP );  //ensure op is enabled
            		}
            		else{
            			data.channel.register( selector, INTEREST_OP, data );
            		}

            		//check if op has been paused before registration moment
            		Object paused = paused_states.get( data.channel );

            		if( paused != null ) {
            			pauseSelects( data.channel );  //pause it
            		}
            	}
            	else{

            		select_fail_data	= data;
            		select_fail_excep	= new Throwable( "select registration: channel is closed" );

            	}
            }catch (Throwable t){

            	Debug.printStackTrace(t);

           		select_fail_data	= data;
           		select_fail_excep	= t;
            }
          }
        }

        paused_states.clear();  //reset after every registration round

      }finally {

      	register_cancel_list_mon.exit();
      }

      if ( select_fail_data != null ){

      	try{
	      	parent.selectFailure(
	      			select_fail_data.listener,
					select_fail_data.channel,
					select_fail_data.attachment,
					select_fail_excep );

      	}catch( Throwable e ){

      		Debug.printStackTrace( e );
      	}
      }

      	//do the actual select

      int count = 0;

      selector_guard.markPreSelectTime();

      long select_start_mono_now = SystemTime.getMonotonousTime();

      long start_hpc = SystemTime.getHighPrecisionCounter();
      
      try{
    	  count = selector.select( timeout );

    	  consec_select_fails = 0;

      }catch( Throwable t ){

    	  consec_select_fails++;

    	  if ( consec_select_fails == 1 ){

    		  consec_select_fails_start = select_start_mono_now;
    	  }

    	  if ( consec_select_fails > 20 && consec_select_fails_start - select_start_mono_now > 16*1000 ){

    		  consec_select_fails = 0;

    		  Debug.out( "Consecutive fail exceeded (" + consec_select_fails + ") - recreating selector" );

    		  closeExistingSelector();

    		  try {  Thread.sleep( 1000 );  }catch( Throwable x ) {x.printStackTrace();}

    		  selector = openNewSelector();

    		  return( 0 );
    	  }

    	  if ( select_start_mono_now - last_select_debug > 5000 ){

    		  last_select_debug = select_start_mono_now;

    		  String msg = t.getMessage();

    		  if ( msg == null || !msg.equalsIgnoreCase( "bad file descriptor" )){

    			  Debug.out( "Caught exception on selector.select() op: " +msg, t );
    		  }
    	  }
    	  
    	  try{  
    		  Thread.sleep( timeout );
    		  
    	  }catch(Throwable e){ 
    		  
    		  e.printStackTrace(); 
    	  }
      }

      long select_hpc_elapsed_ms = ( SystemTime.getHighPrecisionCounter() - start_hpc ) / 1000000L;

      	// do this after the select so that any pending cancels (prior to destroy) are processed
      	// by the selector before we kill it

 	  if ( destroyed ){

 	    closeExistingSelector();

 	    return( 0 );
 	  }

 	 if ( 	MAYBE_BROKEN_SELECT &&
 			!select_is_broken &&
 			( INTEREST_OP == VirtualChannelSelector.OP_READ || INTEREST_OP == VirtualChannelSelector.OP_WRITE )){

 		 if ( selector.selectedKeys().size() == 0 ){

 	 		 Set<SelectionKey> keys = selector.keys();

	 		 for ( SelectionKey key: keys ){

	 			 if (( key.readyOps() & INTEREST_OP ) != 0 ){

	 				select_looks_broken_count++;

	 				break;
	 			 }
	 		 }

	 		 if ( select_looks_broken_count >= 5 ){

	 			 select_is_broken = true;

	 			 if ( !logged_broken_select ){

	 				logged_broken_select = true;

	 				//We always get this on OSX
	 				//Debug.outNoStack( "Select operation looks broken, trying workaround" );
	 			 }
	 		 }
 		 }else{

 			 select_looks_broken_count = 0;
 		 }
 	 }

      /*
      if( INTEREST_OP == VirtualChannelSelector.OP_READ ) {  //TODO
      	select_counts[ round ] = count;
      	round++;
      	if( round == select_counts.length ) {
      		StringBuffer buf = new StringBuffer( select_counts.length * 3 );

      		buf.append( "select_counts=" );
      		for( int i=0; i < select_counts.length; i++ ) {
      			buf.append( select_counts[i] );
      			buf.append( ' ' );
      		}

      		//System.out.println( buf.toString() );
      		round = 0;
      	}
      }
      */

      selector_guard.verifySelectorIntegrity( count, SystemTime.TIME_GRANULARITY_MILLIS /2 );

      if( !selector.isOpen() )  return count;

      int	progress_made_key_count	= 0;
      int	total_key_count			= 0;

      	//notification of ready keys via listener callback

      	// debug handling for channels stuck pending write select for long periods

      Set<SelectionKey>	non_selected_keys = null;

      if ( INTEREST_OP == VirtualChannelSelector.OP_WRITE ){

    	  if ( 	last_write_select_debug == -1 || 
    			  select_start_mono_now - last_write_select_debug > WRITE_SELECTOR_DEBUG_CHECK_PERIOD ){

    		  last_write_select_debug = select_start_mono_now;

    		  non_selected_keys = new HashSet<>(selector.keys());
    	  }
      }

      List<SelectionKey> ready_keys;

      if ( MAYBE_BROKEN_SELECT && select_is_broken ){

    	  Set<SelectionKey> all_keys = selector.keys();

    	  ready_keys = new ArrayList<>();

    	  for ( SelectionKey key: all_keys ){

    		  if (( key.readyOps() & INTEREST_OP ) != 0 ){

    			  ready_keys.add( key );
    		  }
    	  }
      }else{

    	  Set<SelectionKey> selected = selector.selectedKeys();

    	  if ( selected.size() == 0 ){

    		  ready_keys = Collections.emptyList();

    	  }else{

    		  ready_keys = new ArrayList<>(selected);
    	  }
      }

	    if (randomise_keys){

    	  Collections.shuffle( ready_keys );
      }

      Set<SelectionKey>	selected_keys = selector.selectedKeys();

      final int ready_key_size	= ready_keys.size();
	    final int	start_pos 		= next_select_loop_pos++;
      final int	end_pos			= start_pos + ready_key_size;

      for ( int i=start_pos; i<end_pos; i++ ){

    	SelectionKey key = ready_keys.get( i % ready_key_size );

    	total_key_count++;

    	selected_keys.remove( key );

        RegistrationData data = (RegistrationData)key.attachment();

        if ( non_selected_keys != null ){

        	non_selected_keys.remove( key );
        }

        data.last_select_success_mono_time = select_start_mono_now;
        // int	rm_type;

        if( key.isValid() ) {
          if( (key.interestOps() & INTEREST_OP) == 0 ) {  //it must have been paused between select and notification
        	// rm_type = 2;
          }else{

	          if( pause_after_select ) {

	        	try{
	        		key.interestOps( key.interestOps() & ~INTEREST_OP );

	        	}catch( CancelledKeyException e ){

	        	}
	          }

	          boolean	progress_indicator = parent.selectSuccess( data.listener, data.channel, data.attachment );

	          if ( progress_indicator ){

	        	// rm_type = 0;

	        	progress_made_key_count++;

	            data.non_progress_count = 0;

	          }else{

	        	// rm_type = 1;

	            data.non_progress_count++;

	            boolean	loopback_connection = false;

	        	if ( INTEREST_OP != VirtualChannelSelector.OP_ACCEPT ){

	        		SocketChannel sc = (SocketChannel)data.channel;

	        		Socket socket = sc.socket();

	        		InetAddress address = socket.getInetAddress();

	        		if ( address != null ){

	        			loopback_connection = address.isLoopbackAddress();
	        		}
	        	}

	        		// we get no progress triggers when looping back for transcoding due to
	        		// high CPU usage of xcode process - remove debug spew and be more tolerant

	        	if ( loopback_connection ){

	        		if ( data.non_progress_count == 10000 ){

	        			Debug.out( "No progress for " + data.non_progress_count + ", closing connection" );

	        			try{
	        				data.channel.close();

	        			}catch( Throwable e ){

	        				e.printStackTrace();
	        			}
	        		}
	        	}else{

	        		if ( 	data.non_progress_count == 10 ||
	        				data.non_progress_count %100 == 0 && data.non_progress_count > 0 ){

	        			boolean do_log = true;

	        				// seems we can get write-non-progress occurring under 'normal' circumstances so
	        				// back off the early logging in this case

	        			if ( data.non_progress_count == 10 && INTEREST_OP == VirtualChannelSelector.OP_WRITE ){

	        				do_log = false;
	        			}

	        			if ( do_log ){

	        				Debug.out(
	        					"VirtualChannelSelector: No progress for op " + INTEREST_OP +
	        					": listener = " + data.listener.getClass() +
	        					", count = " + data.non_progress_count +
	        					", socket: open = " + data.channel.isOpen() +
	        					(INTEREST_OP==VirtualChannelSelector.OP_ACCEPT?"":
	        						(", connected = " + ((SocketChannel)data.channel).isConnected())));
	        			}

	        			if ( data.non_progress_count == 1000 ){

	        				Debug.out( "No progress for " + data.non_progress_count + ", closing connection" );

	        				try{
	        					data.channel.close();

	        				}catch( Throwable e ){

	        					e.printStackTrace();
	        				}
	        			}
	        		}
	        	}
	          }
	        }
        }else{
          // rm_type = 3;
          key.cancel();
          parent.selectFailure( data.listener, data.channel, data.attachment, new Throwable( "key is invalid" ) );
          // can get this if socket has been closed between select and here
        }

        /*
        if ( rm_trace ){

          	Object	rm_key = data.listener.getClass();

          	int[]	rm_count = (int[])rm_listener_map.get( rm_key );

          	if ( rm_count == null ){

          		rm_count = new int[]{0,0,0,0};

          		rm_listener_map.put( rm_key, rm_count );
          	}

          	rm_count[rm_type]++;
          }
          */
      }

      if ( non_selected_keys != null ){

    	  for( Iterator<SelectionKey> i = non_selected_keys.iterator(); i.hasNext(); ) {

    		  SelectionKey key = i.next();

    	      RegistrationData data = (RegistrationData)key.attachment();

    	      try{
	        	  if (( key.interestOps() & INTEREST_OP) == 0 ) {

	        		  continue;
	        	  }
    	      }catch( CancelledKeyException e ){

    	    	  		// get quite a few of these exceptions, ignore the key

    	    	  continue;
    	      }

    	      long	stall_time = select_start_mono_now - data.last_select_success_mono_time;

    	      if ( stall_time > WRITE_SELECTOR_DEBUG_MAX_TIME ){

    	    	  Logger.log(
    	    		new LogEvent(LOGID,LogEvent.LT_WARNING,"Write select for " + key.channel() + " stalled for " + stall_time ));

    	    	  	// hack - trigger a dummy write select to see if things are still OK

    	          if( key.isValid() ) {

	        		  if( pause_after_select ) {

	        			  key.interestOps( key.interestOps() & ~INTEREST_OP );
	        		  }

	        		  if ( parent.selectSuccess( data.listener, data.channel, data.attachment )){

	        			  data.non_progress_count = 0;
	        		  }
    	          }else{

    	        	  key.cancel();

    	        	  parent.selectFailure( data.listener, data.channel, data.attachment, new Throwable( "key is invalid" ) );
    	          }
    	      }
    	  }
      }


      	// if any of the ready keys hasn't made any progress then enforce minimum sleep period to avoid
      	// spinning

      if ( total_key_count == 0 || progress_made_key_count != total_key_count ){
	      
	      if ( select_hpc_elapsed_ms < timeout/2 ){  

	    	  //ensure that it always takes at least 'timeout' time to complete the select op
	    	  
	      	try{  
	      		Thread.sleep( timeout - select_hpc_elapsed_ms );
	      		
	      	}catch( Throwable e ){
	      		
	      		e.printStackTrace(); 
	      	}
	      }
      }else{
    	  /*
    	  if ( rm_test_fix ){

    	      long time_diff = SystemTime.getCurrentTime() - select_start_time;

    	      if( time_diff < 10 && time_diff >= 0 ) {
    	      	try {  Thread.sleep( 10 - time_diff );  }catch(Throwable e) { e.printStackTrace(); }
    	      }
    	  }
    	  */
      }

      /*
      if ( rm_trace ){

    	  if ( select_start_time - rm_flag_last_log > 10000 ){

    		  rm_flag_last_log	= select_start_time;

    		  Iterator it = rm_listener_map.entrySet().iterator();

    		  String	str = "";

    		  while( it.hasNext()){

    			  Map.Entry	entry = (Map.Entry)it.next();

    			  Class	cla = (Class)entry.getKey();

    			  String	name = cla.getName();
    			  int		pos = name.lastIndexOf('.');
    			  name = name.substring( pos+1 );

    			  int[]	counts = (int[])entry.getValue();

    			  str += (str.length()==0?"":",")+ name + ":" + counts[0]+"/"+counts[1]+"/"+counts[2]+"/"+counts[3];
    		  }

       		  Debug.outNoStack( "RM trace: " + hashCode() + ": op=" + INTEREST_OP + "-" + str );
    	  }
      }
      */

      return count;
    }

    	/**
    	 * Note that you have to ensure that a select operation is performed on the normal select
    	 * loop *after* destroying the selector to actually cause the destroy to occur
    	 */

    public void
    destroy()
    {
    	destroyed	= true;
    }

    protected void closeExistingSelector() {
      for( Iterator<SelectionKey> i = selector.keys().iterator(); i.hasNext(); ) {
        SelectionKey key = i.next();
        RegistrationData data = (RegistrationData)key.attachment();
        parent.selectFailure(data.listener, data.channel, data.attachment, new Throwable( "selector destroyed" ) );
      }

      try{
        selector.close();

        AEDiagnostics.log( "seltrace", "Selector destroyed for '" + parent.getName() + "'," + selector_guard.getType());
      }
      catch( Throwable t ) { t.printStackTrace(); }
    }




    private static class RegistrationData {
        protected final AbstractSelectableChannel channel;
        protected final VirtualChannelSelector.VirtualAbstractSelectorListener listener;
        protected final Object attachment;

        protected int 	non_progress_count;
        protected long	last_select_success_mono_time;

      	RegistrationData( AbstractSelectableChannel _channel, VirtualChannelSelector.VirtualAbstractSelectorListener _listener, Object _attachment ) {
      		channel 		= _channel;
      		listener		= _listener;
      		attachment 		= _attachment;

      		last_select_success_mono_time	= SystemTime.getMonotonousTime();
      	}
      }

}
