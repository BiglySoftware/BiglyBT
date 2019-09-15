/*
 * Created on Nov 19, 2003
 * Created by Alon Rohter
 *
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
package com.biglybt.core.util;

import java.io.*;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

/**
 * Debug-assisting class.
 */
public class Debug {

	private static final boolean STOP_AT_INITIALIZER = System.getProperty("debug.stacktrace.full", "0").equals("0");

	private static final AEDiagnosticsLogger	diag_logger;

	static{
		// store in local variable first, so we can have diag_logger final
		AEDiagnosticsLogger temp_diag_logger = null;
		try{
			temp_diag_logger = AEDiagnostics.getLogger( "debug" );

			temp_diag_logger.setForced( true );

		}catch( Throwable e ){

		}
		diag_logger = temp_diag_logger;
	}


  /**
   * Prints out the given debug message to System.out,
   * prefixed by the calling class name, method and
   * line number.
   */
  public static void out(final String _debug_message) {
    out( _debug_message, null );
  }

  /**
   * Prints out the given exception stacktrace to System.out,
   * prefixed by the calling class name, method and
   * line number.
   */
  public static void out(final Throwable _exception) {
    out( "", _exception );
  }

  public static void
  outNoStack(
  	String		str )
  {
  	outNoStack( str, false );
  }

  public static void
  outNoStack(
  	String		str,
	boolean		stderr)
  {
    diagLoggerLogAndOut("DEBUG::"+ new Date(SystemTime.getCurrentTime()).toString() + "  " + str, stderr );
  }

  public static void
  outDiagLoggerOnly(
  	String		str)
  {
    diagLoggerLog(str);
  }

  /**
   * Prints out the given debug message to System.out,
   * prefixed by the calling class name, method and
   * line number, appending the stacktrace of the given exception.
   */
  public static void out(final String _debug_msg, final Throwable _exception) {
  	if ((_exception instanceof ConnectException) && _exception.getMessage().startsWith("No route to host")) {
  		diagLoggerLog(_exception.toString());
  		return;
  	}
  	if ((_exception instanceof UnknownHostException)) {
  		diagLoggerLog(_exception.toString());
  		return;
  	}
    String header = "DEBUG::";
    header = header + new Date(SystemTime.getCurrentTime()).toString() + "::";
    String className;
    String methodName;
    int lineNumber;
    String	trace_trace_tail = null;

    try {
      throw new Exception();
    }
    catch (Exception e) {
    	StackTraceElement[]	st = e.getStackTrace();

      StackTraceElement first_line = st[2];
      className = first_line.getClassName() + "::";
      methodName = first_line.getMethodName() + "::";
      lineNumber = first_line.getLineNumber();

    	trace_trace_tail = getCompressedStackTrace(e, 3, 200, false);
    }

    diagLoggerLogAndOut(header+className+(methodName)+lineNumber+":", true);
    if (_debug_msg != null && _debug_msg.length() > 0) {
    	diagLoggerLogAndOut("  " + _debug_msg, true);
    }
    if ( trace_trace_tail != null ){
    	diagLoggerLogAndOut( "    " + trace_trace_tail, true);
    }
    if (_exception != null) {
    	diagLoggerLogAndOut(_exception);
    }
  }

  public static String getLastCaller() {
  	return getLastCaller(0);
  }

  public static String getLastCaller(int numToGoBackFurther) {
    try {
      throw new Exception();
    }
    catch (Exception e) {
      // [0] = our throw
      // [1] = the line that called getLastCaller
      // [2] = the line that called the function that has getLastCaller
      StackTraceElement st[] = e.getStackTrace();
      if (st == null || st.length == 0)
      	return "??";
      if (st.length > 3 + numToGoBackFurther)
        return st[3 + numToGoBackFurther].toString();

      return st[st.length - 1].toString();
    }
  }

  public static String getLastCallerShort() {
	  	return getLastCallerShort(0);
	  }

	  public static String getLastCallerShort(int numToGoBackFurther) {
	    try {
	      throw new Exception();
	    }
	    catch (Exception e) {
	      // [0] = our throw
	      // [1] = the line that called getLastCaller
	      // [2] = the line that called the function that has getLastCaller
	      StackTraceElement st[] = e.getStackTrace();
	      StackTraceElement ste;
	      if (st == null || st.length == 0)
	      	return "??";
	      if (st.length > 3 + numToGoBackFurther){
	        ste = st[3 + numToGoBackFurther];
	      }else{
	    	  ste = st[st.length - 1];
	      }
	      String fn = ste.getFileName();

	      if ( fn != null ){
	    	  return( fn + ":" + ste.getLineNumber());
	      }
	      return( ste.toString());
	    }
	  }

  public static void outStackTrace() {
    // skip the last, since they'll most likely be main
	  diagLoggerLogAndOut(getStackTrace(1),false);
  }

  private static String getStackTrace(int endNumToSkip) {
		String sStackTrace = "";
    try {
      throw new Exception();
    }
    catch (Exception e) {
      StackTraceElement st[] = e.getStackTrace();
      for (int i = 1; i < st.length - endNumToSkip; i++) {
        if (!st[i].getMethodName().endsWith("StackTrace"))
        	sStackTrace += st[i].toString() + "\n";
      }
      if (e.getCause() != null)
      	sStackTrace += "\tCaused By: " + getStackTrace(e.getCause()) + "\n";
    }
    return sStackTrace;
  }

	public static void
	killAWTThreads()
	{
		ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();

		killAWTThreads( threadGroup );
	}

	private static String
	getCompressedStackTrace(
		Throwable t,
		int frames_to_skip)
	{
		return getCompressedStackTrace(t, frames_to_skip, 200);
	}


	public static String
	getCompressedStackTrace(
		Throwable t,
		int frames_to_skip,
		int iMaxLines)
	{
		return getCompressedStackTrace(t, frames_to_skip, iMaxLines, true);
	}


	public static String
	getCompressedStackTrace(
		Throwable t,
		int frames_to_skip,
		int iMaxLines,
		boolean showErrString)
	{
		StringBuilder sbStackTrace = new StringBuilder(showErrString ? (t.toString() + "; ") : "");
		StackTraceElement[]	st = t.getStackTrace();

		if (iMaxLines < 0) {
			iMaxLines = st.length + iMaxLines;
			if (iMaxLines < 0) {
				iMaxLines = 1;
			}
		}
		int iMax = Math.min(st.length, iMaxLines + frames_to_skip);
		for (int i = frames_to_skip; i < iMax; i++) {

			if (i > frames_to_skip) {
				sbStackTrace.append(", ");
			}

			String classname = st[i].getClassName();
			String cnShort = classname.substring( classname.lastIndexOf(".")+1);

			if (Constants.IS_CVS_VERSION) {
				if (STOP_AT_INITIALIZER
						&& classname.equals("com.biglybt.ui.swt.Initializer")) {
					sbStackTrace.append("Initializer");
					break;
				}

				if (STOP_AT_INITIALIZER
						&& classname.equals("com.biglybt.core.util.ThreadPool")
						&& st[i].getMethodName().equals("runIt")) {
					sbStackTrace.append("ThreadPool.runIt");
					break;
				}

				if (classname.equals("com.biglybt.core.util.AERunnable")) {
					sbStackTrace.append("AERunnable.run");
					continue;
				}
				// Formatted so it's clickable in eclipse
				// Intellij doesn't detect multiple items on one line, unless you install
				// "Awesome Console"


				// Shorter version  method(filename.java:1234)
				sbStackTrace.append(st[i].getMethodName());
				// the space is needed otherwise Eclipse tries to look up method name
				sbStackTrace.append(" (");
				sbStackTrace.append(st[i].getFileName());
				sbStackTrace.append(':');
				sbStackTrace.append(st[i].getLineNumber());
				sbStackTrace.append(')');
			} else {
				sbStackTrace.append(cnShort);
				sbStackTrace.append("::");
				sbStackTrace.append(st[i].getMethodName());
				sbStackTrace.append("::");
				sbStackTrace.append(st[i].getLineNumber());
			}
		}

		Throwable cause = t.getCause();

		if (cause != null) {
			sbStackTrace.append("\n\tCaused By: ");
			sbStackTrace.append(getCompressedStackTrace(cause, 0));
		}

		return sbStackTrace.toString();
	}

	public static String getStackTrace(boolean bCompressed, boolean bIncludeSelf) {
		return getStackTrace(bCompressed, bIncludeSelf, bIncludeSelf ? 0 : 1, 200);
	}

	public static String getStackTrace(boolean bCompressed, boolean bIncludeSelf,
			int iNumLinesToSkip, int iMaxLines) {
		if (bCompressed)
			return getCompressedStackTrace(bIncludeSelf ? 2 + iNumLinesToSkip
					: 3 + iNumLinesToSkip, iMaxLines);

		// bIncludeSelf not supported gor non Compressed yet
		return getStackTrace(1);
	}

	private static String getCompressedStackTrace(int frames_to_skip,
			int iMaxLines) {
		String trace_trace_tail = null;

		try {
			throw new Exception();
		} catch (Exception e) {
			trace_trace_tail = getCompressedStackTrace(e, frames_to_skip, iMaxLines, false);
		}

		return (trace_trace_tail);
	}

	public static void
	killAWTThreads(
		   ThreadGroup	threadGroup )
	{
		 Thread[] threadList = new Thread[threadGroup.activeCount()];

		 threadGroup.enumerate(threadList);

		 for (int i = 0;	i < threadList.length;	i++){

		 	Thread t = 	threadList[i];

		 	if ( t != null ){

		 		String 	name = t.getName();

		 		if ( name.startsWith( "AWT" )){

		 			out( "Interrupting thread '".concat(t.toString()).concat("'" ));

		 			t.interrupt();
		 		}
			}
		}

		if ( threadGroup.getParent() != null ){

			killAWTThreads(threadGroup.getParent());
		}
	}

	public static void
	dumpThreads(
		String	name )
	{
		out(name+":");

	  	ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();

	  	dumpThreads( threadGroup, "\t" );
	}

   public static void
   dumpThreads(
   		ThreadGroup	threadGroup,
   		String		indent )
   {
	  Thread[] threadList = new Thread[threadGroup.activeCount()];

	  threadGroup.enumerate(threadList);

	  for (int i = 0;	i < threadList.length;	i++){

		Thread t = 	threadList[i];

		if ( t != null ){

		   out( indent.concat("active thread = ").concat(t.toString()).concat(", daemon = ").concat(String.valueOf(t.isDaemon())));
		}
	  }

	  if ( threadGroup.getParent() != null ){

	  	dumpThreads(threadGroup.getParent(),indent+"\t");
	  }
   }

   public static void
   dumpThreadsLoop(
   	final String	name )
   {
   	new AEThread("Thread Dumper")
	   {
		   @Override
		   public void
		   runSupport()
		   {
			   while(true){
				   Debug.dumpThreads(name);

				   try{
				   	Thread.sleep(5000);
				   }catch( Throwable e ){
				   	Debug.printStackTrace( e );
				   }
			   }
		   }
	   }.start();
   }

	public static void
	dumpSystemProperties()
	{
		out( "System Properties:");

 		Properties props = System.getProperties();

 		Iterator it = props.keySet().iterator();

 		while(it.hasNext()){

 			String	name = (String)it.next();

 			out( "\t".concat(name).concat(" = '").concat(props.get(name).toString()).concat("'" ));
 		}
	}

	public static String
	getNestedExceptionMessage(
		Throwable 		e )
	{
		String	last_message	= "";

		while( e != null ){

			String	this_message;

			if ( e instanceof UnknownHostException ){

				this_message = "Unknown host " + e.getMessage();

			}else if ( e instanceof FileNotFoundException ){

				this_message = "File not found: " + e.getMessage();

			}else{

				this_message = e.getMessage();
			}

				// if no exception message then pick up class name. if we have a deliberate
				// zero length string then we assume that the exception can be ignored for
				// logging purposes as it is just delegating

			if ( this_message == null ){

				this_message = e.getClass().getName();

				int	pos = this_message.lastIndexOf(".");

				this_message = this_message.substring( pos+1 ).trim();
			}

			if ( this_message.length() > 0 && !last_message.contains(this_message)){

				last_message	+= (last_message.length()==0?"":", " ) + this_message;
			}

			e	= e.getCause();
		}

		return( last_message );
	}

	public static boolean
	containsException(
		Throwable					error,
		Class<? extends Throwable>	cla )
	{
		if ( error == null ){

			return( false );

		}else if ( cla.isInstance( error )){

			return( true );
		}

		return( containsException( error.getCause(), cla ));
	}

	public static String
	getNestedExceptionMessageAndStack(
		Throwable 		e )
	{
		return( getNestedExceptionMessage(e) + ", " + getCompressedStackTrace( e, 0 ));
	}

	public static String
	getCompressedStackTraceSkipFrames(
		int	frames_to_skip )
	{
		return( getCompressedStackTrace( new Throwable(), frames_to_skip+1, 200, false ));
	}

	public static String
	getCompressedStackTrace()
	{
		return( getCompressedStackTrace( new Throwable(), 1, 200, false ));
	}

	/**
	 *
	 * @param iMaxLines Max # of stack lines.  If < 0, chops off -MaxLines entries from end
	 * @return
	 */
	public static String
	getCompressedStackTrace(int iMaxLines)
	{
		return( getCompressedStackTrace( new Throwable(), 1, iMaxLines, false ));
	}

	public static String
	getExceptionMessage(
		Throwable	e )
	{
		String	message = e.getMessage();

		if ( message == null || message.length() == 0 ){

			message = e.getClass().getName();

			int	pos = message.lastIndexOf(".");

			message = message.substring( pos+1 );

		}else if ( e instanceof ClassNotFoundException ){

			if (!message.toLowerCase().contains("found")){

				message = "Class " + message + " not found";
			}
		}

		return( message );
	}

	public static void printStackTrace(Throwable e) {
		printStackTrace(e, null);
	}


	public static void
	printStackTrace(
		Throwable e,
		Object context)
	{
  	if ((e instanceof ConnectException) && e.getMessage().startsWith("No route to host")) {
  		diagLoggerLog(e.toString());
  		return;
  	}
  	if ((e instanceof UnknownHostException)) {
  		diagLoggerLog(e.toString());
  		return;
  	}
		String header = "DEBUG::";
		header = header + new Date(SystemTime.getCurrentTime()).toString() + "::";
		String className	= "?::";
		String methodName	= "?::";
		int lineNumber		= -1;

	    try {
	        throw new Exception();
	    }catch (Exception f) {
	      	StackTraceElement[]	st = f.getStackTrace();

	      	for (int i=1;i<st.length;i++){
		        StackTraceElement first_line = st[i];
		        className = first_line.getClassName() + "::";
		        methodName = first_line.getMethodName() + "::";
		        lineNumber = first_line.getLineNumber();

		        	// skip stuff generated by the logger

		        if (className.contains(".logging.") ||
		        		className.endsWith( ".Debug::" )){

		        	continue;
		        }

		        break;
	      }
	    }

	    diagLoggerLogAndOut(header+className+(methodName)+lineNumber+":", true);

		try{
			ByteArrayOutputStream	baos = new ByteArrayOutputStream();

			PrintWriter	pw = new PrintWriter( new OutputStreamWriter( baos ));

			if (context!=null) {pw.print("  "); pw.println(context);}
			pw.print("  ");
			e.printStackTrace( pw );

			pw.close();

			String	stack = baos.toString();

			diagLoggerLogAndOut(stack, true );
		}catch( Throwable ignore ){

			e.printStackTrace();
		}
	}

	public static String getStackTrace(Throwable e) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos));

			e.printStackTrace(pw);

			pw.close();

			return baos.toString();

		} catch (Throwable ignore) {
			return "";
		}
	}

	private static void diagLoggerLog(String str) {
		if ( diag_logger == null ){
			System.out.println( str );
		}else{
			diag_logger.log(str);
		}
	}

	private static void
	diagLoggerLogAndOut(
		String	str,
		boolean	stderr )
	{
			// handle possible recursive initialisation problems where the init of diag-logger gets
			// back here....

		if ( diag_logger == null ){
			if ( stderr ){
				System.err.println( str );
			}else{
				System.out.println( str );
			}
			writeEmergencyLog( str );
			
		}else{
			diag_logger.logAndOut( str, stderr );
		}
	}
	private static void
	diagLoggerLogAndOut(
		Throwable e )
	{
			// handle possible recursive initialisation problems where the init of diag-logger gets
			// back here....

		if ( diag_logger == null ){
			e.printStackTrace();
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter( sw );
			
			e.printStackTrace( pw );
			pw.close();
			
			writeEmergencyLog( sw.toString());
			
		}else{
			diag_logger.logAndOut( e );
		}
	}
	
	private static void
	writeEmergencyLog(
		String		str )
	{
		File f = new File( "biglybt_error.log" );	// user.dir relative
		
		try{
			PrintWriter pw = new PrintWriter( new FileWriter( f, true ));
			
			try{
				pw.println( str );
				
			}finally {
				
				pw.close();
			}
		}catch( Throwable e ){ 
		}
	}

	/**
	 * @param key
	 * @return
	 */
	public static String secretFileName(String key) {
		if (key == null)
			return "";

		final String sep = File.separator;
		final String regex = "([\\" + sep + "]?[^\\" + sep + "]{0,3}+)[^\\" + sep
				+ "]*";

		String secretName = key.replaceAll(regex, "$1");
		int iExtensionPos = key.lastIndexOf(".");
		if (iExtensionPos >= 0)
			secretName += key.substring(iExtensionPos);
		return secretName;
	}

	public static void main(String[] args) {
		System.out.println(secretFileName("c:\\temp\\hello there.txt"));
		System.out.println(secretFileName("hello there.txt"));
	}
}
